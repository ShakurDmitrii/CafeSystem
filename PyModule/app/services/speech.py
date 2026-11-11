"""Распознавание речи для голосового набора заказов.

Движок спрятан за ``SpeechTranscriber``: касса и Java backend знают только про
``transcribe(audio, hints)``. Сейчас реализован один движок — faster-whisper,
который работает локально на CPU и не требует интернета после загрузки модели.
"""

from __future__ import annotations

import logging
import threading
from dataclasses import dataclass, field
from typing import Protocol

from app.config import settings
from app.services.model_paths import MODELS_DIR


logger = logging.getLogger(__name__)

# Whisper принимает подсказку не длиннее ~224 токенов; названия меню режем по символам,
# чтобы не зависеть от токенизатора.
MAX_PROMPT_CHARS = 700
MAX_HINTS = 400


class SpeechUnavailableError(RuntimeError):
    """Модель не загружена или движок распознавания выключен."""


class SpeechInputError(ValueError):
    """Аудио пустое или не читается."""


@dataclass(frozen=True)
class Transcription:
    text: str
    language: str | None
    duration_seconds: float
    confidence: float | None
    segments: list[str] = field(default_factory=list)

    def as_dict(self) -> dict[str, object]:
        return {
            "text": self.text,
            "language": self.language,
            "durationSeconds": round(self.duration_seconds, 3),
            "confidence": None if self.confidence is None else round(self.confidence, 4),
        }


class SpeechTranscriber(Protocol):
    def is_ready(self) -> bool: ...

    def warm_up(self) -> None: ...

    def transcribe(self, audio: bytes, hints: list[str]) -> Transcription: ...


def build_initial_prompt(hints: list[str]) -> str | None:
    """Собирает подсказку из названий меню: уникальные, непустые, в пределах лимита."""
    seen: set[str] = set()
    parts: list[str] = []
    for raw in hints[:MAX_HINTS]:
        name = " ".join(str(raw or "").split())
        key = name.casefold()
        if not name or key in seen:
            continue
        seen.add(key)
        parts.append(name)
    if not parts:
        return None
    prompt = ", ".join(parts)
    if len(prompt) > MAX_PROMPT_CHARS:
        prompt = prompt[:MAX_PROMPT_CHARS].rsplit(",", 1)[0]
    return prompt + "."


class FasterWhisperTranscriber:
    """Ленивая обёртка над faster-whisper: модель грузится один раз при первом обращении."""

    def __init__(
        self,
        model_size: str,
        device: str,
        compute_type: str,
        download_root: str | None,
        language: str,
        beam_size: int,
        cpu_threads: int,
    ) -> None:
        self._model_size = model_size
        self._device = device
        self._compute_type = compute_type
        self._download_root = download_root
        self._language = language
        self._beam_size = beam_size
        self._cpu_threads = cpu_threads
        self._model = None
        self._load_error: Exception | None = None
        self._lock = threading.Lock()

    def is_ready(self) -> bool:
        return self._model is not None

    def warm_up(self) -> None:
        self._get_model()

    def _get_model(self):
        if self._model is not None:
            return self._model
        with self._lock:
            if self._model is not None:
                return self._model
            try:
                from faster_whisper import WhisperModel  # импорт здесь: тяжёлая зависимость
            except ImportError as exc:  # pragma: no cover - зависит от окружения
                self._load_error = exc
                raise SpeechUnavailableError("faster-whisper не установлен") from exc
            try:
                logger.info(
                    "Loading speech model %s (%s/%s)", self._model_size, self._device, self._compute_type
                )
                self._model = WhisperModel(
                    self._model_size,
                    device=self._device,
                    compute_type=self._compute_type,
                    download_root=self._download_root,
                    cpu_threads=self._cpu_threads,
                )
                self._load_error = None
            except Exception as exc:  # pragma: no cover - сеть/диск
                self._load_error = exc
                logger.error("Speech model failed to load: %s", exc.__class__.__name__)
                raise SpeechUnavailableError("Модель распознавания речи недоступна") from exc
            return self._model

    def transcribe(self, audio: bytes, hints: list[str]) -> Transcription:
        if not audio:
            raise SpeechInputError("Пустая аудиозапись")
        model = self._get_model()
        import io

        try:
            segments_iter, info = model.transcribe(
                io.BytesIO(audio),
                language=self._language,
                beam_size=self._beam_size,
                initial_prompt=build_initial_prompt(hints),
                vad_filter=True,
                condition_on_previous_text=False,
            )
            segments = list(segments_iter)
        except SpeechUnavailableError:
            raise
        except Exception as exc:
            # faster-whisper/PyAV бросают разные исключения на битое аудио;
            # для кассы это всегда «запись не читается».
            raise SpeechInputError("Аудиозапись не читается") from exc

        texts = [segment.text.strip() for segment in segments if segment.text and segment.text.strip()]
        probs = [segment.avg_logprob for segment in segments if segment.avg_logprob is not None]
        confidence = None
        if probs:
            # avg_logprob лежит примерно в [-1; 0]; переводим в грубую шкалу 0..1.
            confidence = max(0.0, min(1.0, 1.0 + sum(probs) / len(probs)))
        return Transcription(
            text=" ".join(texts),
            language=getattr(info, "language", None),
            duration_seconds=float(getattr(info, "duration", 0.0) or 0.0),
            confidence=confidence,
            segments=texts,
        )


class DisabledTranscriber:
    def is_ready(self) -> bool:
        return False

    def warm_up(self) -> None:
        return None

    def transcribe(self, audio: bytes, hints: list[str]) -> Transcription:
        raise SpeechUnavailableError("Распознавание речи выключено (SPEECH_ENABLED=false)")


def build_transcriber() -> SpeechTranscriber:
    if not settings.SPEECH_ENABLED:
        return DisabledTranscriber()
    download_root = settings.SPEECH_MODEL_DIR or str(MODELS_DIR / "speech")
    return FasterWhisperTranscriber(
        model_size=settings.SPEECH_MODEL,
        device=settings.SPEECH_DEVICE,
        compute_type=settings.SPEECH_COMPUTE_TYPE,
        download_root=download_root,
        language=settings.SPEECH_LANGUAGE,
        beam_size=settings.SPEECH_BEAM_SIZE,
        cpu_threads=settings.SPEECH_CPU_THREADS,
    )


transcriber: SpeechTranscriber = build_transcriber()


def warm_up_in_background() -> None:
    """Грузит модель в фоновом потоке при старте, чтобы первый заказ не ждал."""
    if not settings.SPEECH_ENABLED or not settings.SPEECH_PRELOAD:
        return

    def _run() -> None:
        try:
            transcriber.warm_up()
            logger.info("Speech model ready")
        except SpeechUnavailableError:
            logger.warning("Speech model is not available; voice ordering disabled until restart")

    threading.Thread(target=_run, name="speech-warm-up", daemon=True).start()
