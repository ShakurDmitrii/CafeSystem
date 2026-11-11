from __future__ import annotations

import json
import logging
from typing import Annotated, Any

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
from starlette.concurrency import run_in_threadpool

from app.config import settings
from app.security import require_service_token
from app.services import speech
from app.services.speech import SpeechInputError, SpeechUnavailableError


logger = logging.getLogger(__name__)
router = APIRouter(
    prefix="/api/speech",
    tags=["Speech"],
    dependencies=[Depends(require_service_token)],
)

ALLOWED_AUDIO_PREFIXES = ("audio/", "video/webm", "application/octet-stream")


def _parse_hints(raw: str | None) -> list[str]:
    if not raw:
        return []
    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise HTTPException(
            status_code=422,
            detail={"code": "SPEECH_INPUT_INVALID", "message": "hints должен быть JSON-массивом строк"},
        ) from exc
    if not isinstance(parsed, list) or not all(isinstance(item, str) for item in parsed):
        raise HTTPException(
            status_code=422,
            detail={"code": "SPEECH_INPUT_INVALID", "message": "hints должен быть JSON-массивом строк"},
        )
    return parsed


@router.get("/status")
async def speech_status() -> dict[str, Any]:
    return {
        "enabled": settings.SPEECH_ENABLED,
        "ready": speech.transcriber.is_ready(),
        "model": settings.SPEECH_MODEL if settings.SPEECH_ENABLED else None,
        "language": settings.SPEECH_LANGUAGE,
    }


@router.post("/transcribe")
async def transcribe_endpoint(
    audio: Annotated[UploadFile, File(description="Запись фразы кассира (webm/opus, wav, ogg, mp3)")],
    hints: Annotated[str | None, Form(description="JSON-массив названий меню для подсказки модели")] = None,
) -> dict[str, Any]:
    content_type = (audio.content_type or "").lower()
    if content_type and not content_type.startswith(ALLOWED_AUDIO_PREFIXES):
        raise HTTPException(
            status_code=422,
            detail={"code": "SPEECH_INPUT_INVALID", "message": f"Неподдерживаемый тип аудио: {content_type}"},
        )
    payload = await audio.read(settings.SPEECH_MAX_AUDIO_BYTES + 1)
    if len(payload) > settings.SPEECH_MAX_AUDIO_BYTES:
        raise HTTPException(
            status_code=413,
            detail={"code": "SPEECH_INPUT_INVALID", "message": "Слишком длинная запись"},
        )
    hint_list = _parse_hints(hints)

    try:
        result = await run_in_threadpool(speech.transcriber.transcribe, payload, hint_list)
    except SpeechInputError as exc:
        raise HTTPException(
            status_code=422,
            detail={"code": "SPEECH_INPUT_INVALID", "message": str(exc)},
        ) from exc
    except SpeechUnavailableError as exc:
        raise HTTPException(
            status_code=503,
            detail={"code": "SPEECH_UNAVAILABLE", "message": str(exc)},
        ) from exc
    except Exception as exc:
        logger.exception("Speech transcription failed")
        raise HTTPException(
            status_code=500,
            detail="Внутренняя ошибка распознавания речи",
        ) from exc

    return result.as_dict()
