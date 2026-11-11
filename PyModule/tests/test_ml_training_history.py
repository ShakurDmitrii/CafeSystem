import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import numpy as np
import pandas as pd

from app.services import service


def _frame(records):
    return service._prepare_training_frame(records)


class ZeroSaleDaysTests(unittest.TestCase):
    def test_missing_open_days_inside_dish_lifetime_become_zero_sales(self):
        records = [dict(ingredients=["rice", "salmon"], sales=3, date=f"2026-08-0{day}", rollName="Филадельфия")
                   for day in (1, 3, 5)]
        records += [dict(ingredients=["rice", "eel"], sales=day, date=f"2026-08-0{day}", rollName="Унаги")
                    for day in range(1, 8)]
        frame, restored = service._fill_zero_sale_days(_frame(records))

        self.assertTrue(restored)
        philadelphia = frame[frame["dish"] == "rollName:Филадельфия"].set_index("date")["sales"]
        # нули только между первой и последней продажей блюда, не после неё
        self.assertEqual(list(philadelphia.index.day), [1, 2, 3, 4, 5])
        self.assertEqual(list(philadelphia), [3, 0, 3, 0, 3])
        self.assertEqual(len(frame[frame["dish"] == "rollName:Унаги"]), 7)

    def test_zero_day_uses_recipe_from_the_latest_previous_sale(self):
        records = [
            dict(ingredients=["rice", "salmon"], sales=2, date="2026-08-01", dishId=7),
            dict(ingredients=["rice", "salmon", "cheese"], sales=2, date="2026-08-03", dishId=7),
            dict(ingredients=["rice", "salmon", "cheese"], sales=1, date="2026-08-05", dishId=7),
        ] + [dict(ingredients=["rice"], sales=day + 1, date=f"2026-08-0{day}", dishId=8) for day in range(1, 8)]
        frame, _ = service._fill_zero_sale_days(_frame(records))
        zeros = frame[(frame["dish"] == "dishId:7") & (frame["sales"] == 0)].set_index("date")["ingredients"]
        self.assertEqual(zeros.loc[pd.Timestamp("2026-08-02")], ["rice", "salmon"])
        self.assertEqual(zeros.loc[pd.Timestamp("2026-08-04")], ["rice", "salmon", "cheese"])

    def test_without_dish_names_days_are_not_invented(self):
        records = [dict(ingredients=["rice"], sales=day, date=f"2026-08-{day:02d}") for day in range(1, 12)]
        frame, restored = service._fill_zero_sale_days(_frame(records))
        self.assertFalse(restored)
        self.assertEqual(len(frame), 11)


class RecencyWeightTests(unittest.TestCase):
    def test_weight_halves_every_year(self):
        dates = pd.Series(pd.to_datetime(["2024-10-04", "2025-10-04", "2026-10-04"]))
        weights = service._recency_weights(dates)
        np.testing.assert_allclose(weights, [0.25, 0.5, 1.0], rtol=0.01)


class FullHistoryTrainingTests(unittest.TestCase):
    def _history(self):
        rng = np.random.default_rng(1)
        records = []
        for day in pd.date_range("2025-01-01", periods=120):
            for name, ingredients, rate in (("Филадельфия", ["rice", "salmon", "cheese"], 6),
                                            ("Калифорния", ["rice", "crab", "cucumber"], 3),
                                            ("Огурец", ["rice", "cucumber"], 1)):
                qty = int(rng.poisson(rate * (1.4 if day.weekday() >= 4 else 1.0)))
                if qty:
                    records.append(dict(ingredients=ingredients, sales=qty, date=day.date().isoformat(), rollName=name))
        return records

    def test_ensemble_is_trained_saved_and_loaded_back(self):
        with tempfile.TemporaryDirectory() as directory, \
                patch.object(service, "MODELS_DIR", Path(directory)), \
                patch.object(service, "MODEL_BUNDLE_PATH", Path(directory) / "bundle.joblib"), \
                patch.multiple(service, model=None, mlb=None, model_metadata={}, popular_ingredient_pairs=[]):
            result = service.train_model(self._history())

            self.assertEqual(result["target"], service.TARGET_DAILY)
            self.assertIn("Poisson", result["modelType"])
            self.assertGreater(result["zeroSaleDaysAdded"], 0)
            self.assertEqual(result["trainingStartDate"], "2025-01-01")
            self.assertEqual(set(result["validation"]["componentMae"]), {"trees", "linear"})

            self.assertTrue(service.load_model())
            popular = service.predict_single(["rice", "salmon", "cheese"], "2025-05-02")
            rare = service.predict_single(["rice", "cucumber"], "2025-05-02")
            self.assertGreater(popular, rare)


if __name__ == "__main__":
    unittest.main()
