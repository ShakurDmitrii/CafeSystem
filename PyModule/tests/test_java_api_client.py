import unittest

from app.clients.java_api import JavaApiClient, JavaApiError
from app.config import settings


class _FakeResponse:
    def __init__(self, status: int, payload=None, json_error: Exception | None = None):
        self.status = status
        self.payload = payload
        self.json_error = json_error

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, traceback):
        return False

    async def json(self):
        if self.json_error:
            raise self.json_error
        return self.payload


class _FakeSession:
    def __init__(self, response: _FakeResponse):
        self.response = response
        self.request = None

    def get(self, url, **kwargs):
        self.request = {"url": url, **kwargs}
        return self.response


class JavaApiClientTests(unittest.IsolatedAsyncioTestCase):
    async def test_request_contains_token_version_and_configured_timeout(self) -> None:
        session = _FakeSession(_FakeResponse(200, payload=[]))
        client = JavaApiClient("http://java:8080/")
        client.session = session  # type: ignore[assignment]

        result = await client.get("/api/ml/data/sales")

        self.assertEqual(result, [])
        self.assertEqual(session.request["url"], "http://java:8080/api/ml/data/sales")
        self.assertEqual(
            session.request["headers"]["X-Service-Token"],
            settings.INTERNAL_SERVICE_TOKEN.get_secret_value(),
        )
        self.assertEqual(
            session.request["headers"]["X-Contract-Version"],
            settings.INTERNAL_API_CONTRACT_VERSION,
        )
        self.assertEqual(
            session.request["timeout"].total,
            settings.JAVA_API_TIMEOUT_SECONDS,
        )

    async def test_invalid_json_becomes_generic_bad_gateway(self) -> None:
        client = JavaApiClient("http://java:8080")
        client.session = _FakeSession(  # type: ignore[assignment]
            _FakeResponse(200, json_error=ValueError("internal response fragment"))
        )

        with self.assertRaises(JavaApiError) as raised:
            await client.get("/api/ml/data/sales")

        self.assertEqual(raised.exception.status, 502)
        self.assertEqual(str(raised.exception), "Java API unavailable")

    async def test_http_contract_error_preserves_status(self) -> None:
        client = JavaApiClient("http://java:8080")
        client.session = _FakeSession(_FakeResponse(409))  # type: ignore[assignment]

        with self.assertRaises(JavaApiError) as raised:
            await client.get("/api/ml/data/sales")

        self.assertEqual(raised.exception.status, 409)


if __name__ == "__main__":
    unittest.main()
