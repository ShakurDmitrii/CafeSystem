# app/services/ml_service.py
import pandas as pd
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import MultiLabelBinarizer
import joblib
import logging
import random
from typing import List
from xgboost import XGBRegressor

logger = logging.getLogger(__name__)

# Глобальные переменные модели
model = None
mlb = None

def load_model():
    """Загружает модель и mlb из файлов, если они есть"""
    global model, mlb
    try:
        model = joblib.load("model.pkl")
        mlb = joblib.load("mlb.pkl")
        logger.info("ML модель загружена")
    except FileNotFoundError:
        logger.warning("Модель не загружена, нужны данные для обучения")


def predict_single(ingredients: List[str]) -> float:
    """Предсказание для одного ролла"""
    if model is None:
        raise RuntimeError("ML модель не обучена")
    # Заглушка: случайное число
    return random.uniform(50, 200)


def predict_batch(rolls: List[List[str]]) -> list:
    """Пакетное предсказание"""
    results = []
    for ingredients in rolls:
        try:
            prediction = predict_single(ingredients)
            results.append({
                "ingredients": ingredients,
                "predicted_sales": float(prediction)
            })
        except Exception as e:
            results.append({
                "ingredients": ingredients,
                "error": str(e)
            })
    return results


def train_model(records: list):
    global model, mlb
    if not records:
        raise ValueError("Нет данных для обучения")

    df = pd.DataFrame(records)

    # Преобразуем ингредиенты в бинарные фичи
    mlb = MultiLabelBinarizer()
    X_ingredients = mlb.fit_transform(df['ingredients'])

    # Можно добавить числовые признаки, например dayOfWeek, month
    extra_features = df[['dayOfWeek', 'month']].to_numpy() if 'dayOfWeek' in df.columns else None
    if extra_features is not None:
        import numpy as np
        X = np.hstack([X_ingredients, extra_features])
    else:
        X = X_ingredients

    y = df['sales']

    # Разделяем на train/test
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

    # XGBoost регрессор
    model = XGBRegressor(
        n_estimators=300,
        max_depth=6,
        learning_rate=0.05,
        subsample=0.8,
        colsample_bytree=0.8,
        random_state=42
    )
    model.fit(X_train, y_train, eval_set=[(X_test, y_test)], verbose=False)

    # Можно проверить метрику
    rmse = ((model.predict(X_test) - y_test) ** 2).mean() ** 0.5
    print(f"Training completed, RMSE on test: {rmse:.2f}")

    # Сохраняем модель и mlb
    joblib.dump(model, "xgb_model.pkl")
    joblib.dump(mlb, "mlb.pkl")

    return {"status": "trained", "records": len(records), "rmse": rmse}
