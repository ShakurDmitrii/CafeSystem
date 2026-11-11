import os
import unittest
from unittest.mock import patch

os.environ.setdefault("INTERNAL_SERVICE_TOKEN", "test-internal-service-token")

from fastapi.testclient import TestClient

from app.main import app
from app.services import speech
from app.services.speech import (
    SpeechInputError,
    SpeechUnavailableError,
    Transcription,
    build_initial_prompt,
)


HEADERS = {"X-Service-Token": "test-internal-service-token", "X-Contract-Version": "1"}


class _FakeTranscriber:
    def __init__(self, result=None, error: Exception | None = None):
        self.result = result
        self.error = error
        self.calls: list[tuple[bytes, list[str]]] = []

    def is_ready(self) -> bool:
        return True

    def warm_up(self) -> None:
        return None

    def transcribe(self, audio: bytes, hints: list[str]) -> Transcription:
        self.calls.append((audio, hints))
        if self.error:
            raise self.error
        return self.result


class InitialPromptTests(unittest.TestCase):
    def test_prompt_deduplicates_and_trims_names(self) -> None:
        prompt = build_initial_prompt(["Филадельфия", " филадельфия ", "Эби макки", "", None])

        self.assertEqual(prompt, "Филадельфия, Эби макки.")

    def test_prompt_is_capped_by_length(self) -> None:
        prompt = build_initial_prompt([f"Блюдо номер {i}" for i in range(500)])

        self.assertIsNotNone(prompt)
        self.assertLessEqual(len(prompt), speech.MAX_PROMPT_CHARS + 1)
        self.assertTrue(prompt.endswith("."))

    def test_empty_hints_give_no_prompt(self) -> None:
        self.assertIsNone(build_initial_prompt([]))


class SpeechEndpointTests(unittest.TestCase):
    def setUp(self) -> None:
        self.client = TestClient(app)

    def test_requires_service_token(self) -> None:
        response = self.client.post("/api/speech/transcribe", files={"audio": ("a.webm", b"xx", "audio/webm")})

        self.assertEqual(response.status_code, 401)

    def test_transcribes_audio_and_passes_hints(self) -> None:
        fake = _FakeTranscriber(Transcription(text="филадельфия кофе два", language="ru", duration_seconds=2.5, confidence=0.8))
        with patch.object(speech, "transcriber", fake):
            response = self.client.post(
                "/api/speech/transcribe",
                headers=HEADERS,
                files={"audio": ("phrase.webm", b"\x1aE\xdf\xa3fake", "audio/webm")},
                data={"hints": '["Филадельфия", "Кофе Американо"]'},
            )

        self.assertEqual(response.status_code, 200, response.text)
        body = response.json()
        self.assertEqual(body["text"], "филадельфия кофе два")
        self.assertEqual(body["durationSeconds"], 2.5)
        self.assertEqual(fake.calls[0][1], ["Филадельфия", "Кофе Американо"])

    def test_invalid_hints_are_rejected(self) -> None:
        with patch.object(speech, "transcriber", _FakeTranscriber()):
            response = self.client.post(
                "/api/speech/transcribe",
                headers=HEADERS,
                files={"audio": ("phrase.webm", b"abc", "audio/webm")},
                data={"hints": '{"not": "a list"}'},
            )

        self.assertEqual(response.status_code, 422)
        self.assertEqual(response.json()["detail"]["code"], "SPEECH_INPUT_INVALID")

    def test_unreadable_audio_is_a_domain_error(self) -> None:
        fake = _FakeTranscriber(error=SpeechInputError("Аудиозапись не читается"))
        with patch.object(speech, "transcriber", fake):
            response = self.client.post(
                "/api/speech/transcribe",
                headers=HEADERS,
                files={"audio": ("phrase.webm", b"abc", "audio/webm")},
            )

        self.assertEqual(response.status_code, 422)
        self.assertEqual(response.json()["detail"]["code"], "SPEECH_INPUT_INVALID")

    def test_missing_model_is_reported_as_unavailable(self) -> None:
        fake = _FakeTranscriber(error=SpeechUnavailableError("Модель недоступна"))
        with patch.object(speech, "transcriber", fake):
            response = self.client.post(
                "/api/speech/transcribe",
                headers=HEADERS,
                files={"audio": ("phrase.webm", b"abc", "audio/webm")},
            )

        self.assertEqual(response.status_code, 503)
        self.assertEqual(response.json()["detail"]["code"], "SPEECH_UNAVAILABLE")

    def test_non_audio_upload_is_rejected(self) -> None:
        with patch.object(speech, "transcriber", _FakeTranscriber()):
            response = self.client.post(
                "/api/speech/transcribe",
                headers=HEADERS,
                files={"audio": ("phrase.txt", b"abc", "text/plain")},
            )

        self.assertEqual(response.status_code, 422)

    def test_status_reports_readiness(self) -> None:
        with patch.object(speech, "transcriber", _FakeTranscriber()):
            response = self.client.get("/api/speech/status", headers=HEADERS)

        self.assertEqual(response.status_code, 200)
        self.assertTrue(response.json()["ready"])


if __name__ == "__main__":
    unittest.main()
