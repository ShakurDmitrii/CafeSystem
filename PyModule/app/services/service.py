# app/services/ml_service.py
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List
import pandas as pd
import numpy as np
from sklearn.ensemble import RandomForestRegressor
from sklearn.preprocessing import MultiLabelBinarizer
import joblib
import logging
from itertools import combinations
from collections import defaultdict
import random

logger = logging.getLogger(__name__)

# Создаем отдельное приложение для ML
ml_app = FastAPI(title="Sushi ML Service")


class MLRequest(BaseModel):
    ingredients: List[str]


class BatchMLRequest(BaseModel):
    rolls: List[MLRequest]


# Модель загружается один раз при старте
model = None
mlb = None
pair_weights = {}
pair_list = []


@ml_app.on_event("startup")
async def load_model():
    global model, mlb
    try:
        model = joblib.load("model.pkl")
        mlb = joblib.load("mlb.pkl")
        logger.info("ML модель загружена")
    except:
        logger.warning("Модель не загружена, нужны данные для обучения")


@ml_app.post("/predict/single")
async def predict_single(request: MLRequest):
    """Предсказание для одного ролла"""
    if model is None:
        raise HTTPException(status_code=503, detail="ML модель не обучена")

    try:
        prediction = _predict_sales(request.ingredients)
        return {
            "predicted_sales": float(prediction),
            "ingredients": request.ingredients,
            "confidence": 0.85  # Заглушка
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@ml_app.post("/predict/batch")
async def predict_batch(request: BatchMLRequest):
    """Пакетное предсказание"""
    results = []
    for roll in request.rolls:
        try:
            prediction = _predict_sales(roll.ingredients)
            results.append({
                "ingredients": roll.ingredients,
                "predicted_sales": float(prediction)
            })
        except Exception as e:
            results.append({
                "ingredients": roll.ingredients,
                "error": str(e)
            })

    return {"results": results}


@ml_app.post("/train")
async def train_model(data: dict):
    """Обучение модели на данных от Java"""
    try:
        # Преобразуем данные в DataFrame
        records = data.get("records", [])
        df = pd.DataFrame(records)

        # Обучаем модель
        global model, mlb
        model, mlb = _train_ml_model(df)

        # Сохраняем модель
        joblib.dump(model, "model.pkl")
        joblib.dump(mlb, "mlb.pkl")

        return {"status": "trained", "records": len(records)}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@ml_app.get("/health")
async def health():
    return {"status": "ready", "model_loaded": model is not None}


# Внутренние функции ML
def _predict_sales(ingredients: List[str]) -> float:
    """Логика предсказания"""
    # Твоя ML логика здесь
    return random.uniform(50, 200)  # Заглушка


def _train_ml_model(df: pd.DataFrame):
    """Обучение модели"""
    # Твоя логика обучения здесь
    mlb = MultiLabelBinarizer()
    X = mlb.fit_transform(df['ingredients'])
    model = RandomForestRegressor()
    model.fit(X, df['sales'])
    return model, mlb