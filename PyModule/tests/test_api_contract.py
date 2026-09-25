import os
import unittest
from unittest.mock import patch

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

    def test_incompatible_contract_version_is_rejected(self) -> None:
        response = self.client.get(
            "/api/ml/info",
            headers={
                "X-Service-Token": "test-internal-service-token",
                "X-Contract-Version": "999",
            },
        )

        self.assertEqual(response.status_code, 409)
        self.assertIn("верси", response.json()["detail"].lower())

    def test_python_printing_is_not_exposed(self) -> None:
        for path in ("/print", "/print/order", "/print/test-text"):
            with self.subTest(path=path):
                response = self.client.post(
                    path,
                    headers={"X-Service-Token": "test-internal-service-token"},
                    json={},
                )
                self.assertEqual(response.status_code, 404)

    def test_training_data_error_has_explicit_domain_code(self):
        records = [dict(ingredients=["рис"], sales=i + 1, date=f"2026-08-{i+1:02d}", rollName="Ролл") for i in range(10)]
        with patch("app.services.service.train_model", side_effect=ValueError("Недостаточно данных")):
            response = self.client.post("/api/ml/train", json={"records": records},
                                        headers={"X-Service-Token": "test-internal-service-token"})
        self.assertEqual(response.status_code, 422)
        self.assertEqual(response.json()["detail"]["code"], "ML_INPUT_INVALID")

    def test_invalid_genetic_bounds_are_rejected(self):
        for constraints in (dict(minIngredients=6, maxIngredients=2), dict(generations=1.5),
                            dict(populationSize=-1), dict(minProfitMargin=2)):
            response = self.client.post("/api/ml/optimize", json={"constraints": constraints},
                                        headers={"X-Service-Token": "test-internal-service-token"})
            self.assertEqual(response.status_code, 422)


if __name__ == "__main__":
    unittest.main()
