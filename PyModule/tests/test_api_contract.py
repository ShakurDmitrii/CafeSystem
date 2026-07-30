import os
import unittest

os.environ.setdefault("INTERNAL_SERVICE_TOKEN", "test-internal-service-token")

from fastapi.testclient import TestClient

from app.main import app


class ApiContractTests(unittest.TestCase):
    def setUp(self) -> None:
        self.client = TestClient(app)

    def test_liveness_is_public(self) -> None:
        response = self.client.get("/health")

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["status"], "ok")

    def test_internal_endpoints_require_service_token(self) -> None:
        response = self.client.get("/api/ml/info")

        self.assertEqual(response.status_code, 401)

    def test_model_info_accepts_service_token(self) -> None:
        response = self.client.get(
            "/api/ml/info",
            headers={"X-Service-Token": "test-internal-service-token"},
        )

        self.assertEqual(response.status_code, 200)
        self.assertIn("modelLoaded", response.json())

    def test_python_printing_is_not_exposed(self) -> None:
        for path in ("/print", "/print/order", "/print/test-text"):
            with self.subTest(path=path):
                response = self.client.post(
                    path,
                    headers={"X-Service-Token": "test-internal-service-token"},
                    json={},
                )
                self.assertEqual(response.status_code, 404)


if __name__ == "__main__":
    unittest.main()
