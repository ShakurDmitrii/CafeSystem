import unittest

from app.services import service


class _FakeBinarizer:
    classes_ = ["рис", "лосось"]

    def transform(self, rows):
        return [[1, 1]]


class _FakeModel:
    n_features_in_ = 2


class MlFeatureValidationTests(unittest.TestCase):
    def test_unknown_ingredients_are_rejected(self) -> None:
        old_model = service.model
        old_mlb = service.mlb
        try:
            service.model = _FakeModel()
            service.mlb = _FakeBinarizer()

            with self.assertRaisesRegex(ValueError, "Неизвестные ингредиенты"):
                service._build_features(["рис", "авокадо"], None)
        finally:
            service.model = old_model
            service.mlb = old_mlb


if __name__ == "__main__":
    unittest.main()
