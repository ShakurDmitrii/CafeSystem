# app/services/test_training_service.py
import pandas as pd
from sklearn.ensemble import HistGradientBoostingRegressor
from sklearn.preprocessing import MultiLabelBinarizer
import joblib
import random
from datetime import datetime, timedelta

from app.services.model_paths import MODELS_DIR, MODEL_PATH, MLB_PATH, LEGACY_MODEL_PATH, LEGACY_MLB_PATH

class TestTrainingService:
    def __init__(self):
        self.model = None
        self.mlb = None

    def generate_test_data(self, n_records=1000):
        """Генерация тестовых данных"""
        rolls = ["ролл с лососем", "рис с тунцом", "калифорния", "авокадо ролл", "дракон ролл"]
        ingredients_options = [
            ["рис", "лосось"], ["рис", "тунц"], ["рис", "авокадо", "угорь"], ["рис", "краб"], ["рис", "лосось", "авокадо"]
        ]
        records = []
        for _ in range(n_records):
            idx = random.randint(0, len(rolls)-1)
            records.append({
                "rollName": rolls[idx],
                "ingredients": ingredients_options[idx],
                "sales": random.randint(20, 200),
                "date": (datetime.now() - timedelta(days=random.randint(0, 365))).strftime("%Y-%m-%d"),
                "dayOfWeek": random.randint(1,7),
                "month": random.randint(1,12),
                "isActive": True
            })
        return records

    def train_on_test_data(self, n_records=1000):
        """Дополнительное обучение на тестовых данных"""
        records = self.generate_test_data(n_records)
        df = pd.DataFrame(records)
        self.mlb = MultiLabelBinarizer()
        X = self.mlb.fit_transform(df['ingredients'])
        y = df['sales']
        self.model = HistGradientBoostingRegressor()
        self.model.fit(X, y)

        # Сохраняем модель в единое место + legacy копию для совместимости.
        MODELS_DIR.mkdir(parents=True, exist_ok=True)
        joblib.dump(self.model, MODEL_PATH)
        joblib.dump(self.mlb, MLB_PATH)
        joblib.dump(self.model, LEGACY_MODEL_PATH)
        joblib.dump(self.mlb, LEGACY_MLB_PATH)

        return {
            "status": "trained_on_test_data",
            "records": len(records),
            "model_type": "HistGradientBoostingRegressor"
        }

    def predict(self, ingredients: list):
        """Предсказание на тестовой модели"""
        if self.model is None or self.mlb is None:
            raise RuntimeError("Модель не обучена")
        X = self.mlb.transform([ingredients])
        return float(self.model.predict(X)[0])
