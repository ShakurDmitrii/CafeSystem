import unittest
import numpy as np
from unittest.mock import patch

from app.services import service
from app.services.dish_generator import _ingredient_pool, _build_sales_weights


class MlQualityTests(unittest.TestCase):
    def test_r2_is_not_prediction_confidence(self):
        with patch.object(service, "get_model_info", return_value={"validationR2": 0.95}):
            self.assertIsNone(service.get_confidence_score())

    def test_constant_target_cannot_replace_model(self):
        records = [dict(ingredients=["rice"], sales=1, date=f"2026-08-{i:02d}")
                   for i in range(1, 16)]
        with patch.object(service, "_atomic_dump_bundle") as save:
            with self.assertRaisesRegex(ValueError, "одинаков"):
                service.train_model(records)
            save.assert_not_called()

    def test_training_needs_several_distinct_days(self):
        records = [dict(ingredients=["rice"], sales=i, date="2026-08-01")
                   for i in range(10)]
        with self.assertRaisesRegex(ValueError, "дат|дн"):
            service._prepare_training_frame(records)

    def test_ambiguous_ingredient_names_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "назван"):
            _ingredient_pool([
                dict(name="Рис", costPerUnit=1, unit="g"),
                dict(name=" рис ", costPerUnit=2, unit="g"),
            ])

    def test_missing_cost_and_non_mass_units_are_not_invented(self):
        names, costs, _, _ = _ingredient_pool([
            dict(name="rice", costPerUnit=0, unit="g"),
            dict(name="unknown", unit="g"),
            dict(name="sauce", costPerUnit=1, unit="ml"),
            dict(name="nori", costPerUnit=10, unit="pcs"),
        ])
        self.assertEqual(names, ["rice"])
        self.assertEqual(costs["rice"], 0)

    def test_zero_sales_do_not_become_one_sale(self):
        self.assertEqual(_build_sales_weights([dict(ingredients=["rice"], quantity=0)]), {})

    def test_validation_splits_whole_dates_and_final_fit_uses_all_records(self):
        fits = []
        class RecordingModel:
            def __init__(self, **kwargs):
                pass
            def fit(self, features, target, **kwargs):
                fits.append((features.copy(), target.copy(), kwargs))
                self.n_features_in_ = features.shape[1]
                return self
            def predict(self, features):
                return np.ones(len(features))
        records = [dict(ingredients=["rice"], sales=(day % 3) + dish + 1, date=f"2026-08-{day:02d}")
                   for day in range(1, 12) for dish in range(2)]
        with patch.object(service, "XGBRegressor", RecordingModel), patch.object(service, "_atomic_dump_bundle") as save, \
             patch.multiple(service, model=None, mlb=None, model_metadata={}, popular_ingredient_pairs=[]):
            result = service.train_model(records)
            self.assertEqual(result["target"], "daily_quantity_on_sale_days")
            self.assertEqual(len(fits[0][1]), 16)
            self.assertEqual(len(fits[1][1]), 22)
            self.assertEqual(save.call_args.args[0]["metadata"]["validationStartDate"], "2026-08-09")

    def test_constant_validation_target_has_no_fake_perfect_r2(self):
        records = [dict(ingredients=["rice"], sales=(day % 3) + 1 if day < 9 else 1,
                        date=f"2026-08-{day:02d}") for day in range(1, 12)]
        with patch.object(service, "_atomic_dump_bundle"), \
             patch.multiple(service, model=None, mlb=None, model_metadata={}, popular_ingredient_pairs=[]):
            self.assertIsNone(service.train_model(records)["validation"]["r2"])
