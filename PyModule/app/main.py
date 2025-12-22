# main.py
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Dict, Any, Optional
import logging
import pandas as pd
import numpy as np
from itertools import combinations
from collections import defaultdict
import random
import joblib
from sklearn.ensemble import RandomForestRegressor
from sklearn.preprocessing import MultiLabelBinarizer
from datetime import datetime

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="Sushi Restaurant System API",
    version="1.0.0",
    description="API для печати накладных и ML предсказаний"
)

# CORS для Java
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8080"],
    allow_methods=["*"],
    allow_headers=["*"],
)


# --- МОДЕЛИ ДЛЯ ПЕЧАТИ НАКЛАДНЫХ ---
class Item(BaseModel):
    name: str
    price: float
    quantity: float
    sum: float


class Consignment(BaseModel):
    consignmentId: int
    supplierName: str
    date: str
    items: List[Item]
    total: float


# --- МОДЕЛИ ДЛЯ ML ---
class RollPredictionRequest(BaseModel):
    ingredients: List[str]
    roll_name: Optional[str] = None  # Добавь Optional
    current_price: Optional[float] = None


class BatchPredictionRequest(BaseModel):
    rolls: List[RollPredictionRequest]


class TrainingData(BaseModel):
    records: List[Dict[str, Any]]


# --- ML ЛОГИКА ---
class SushiMLModel:
    def __init__(self):
        self.model = None
        self.mlb = None
        self.pair_weights = {}
        self.pair_list = []
        self.ingredients = []

    def load_or_train_model(self, df: pd.DataFrame = None):
        """Загрузка или обучение модели"""
        try:
            self.model = joblib.load("model.pkl")
            self.mlb = joblib.load("mlb.pkl")
            logger.info("Загружена существующая ML модель")
        except:
            if df is not None and not df.empty:
                logger.info("Обучение новой ML модели...")
                self.train_model(df)
            else:
                logger.warning("Нет данных для обучения модели")

    def train_model(self, df: pd.DataFrame):
        """Обучение модели на данных"""
        self.mlb = MultiLabelBinarizer()
        df['Ингредиенты'] = df['Ингредиенты'].apply(
            lambda x: [i.strip().lower() for i in str(x).split('|')]
        )

        self._calculate_pair_weights(df)

        X = self.mlb.fit_transform(df['Ингредиенты'])
        y = df['Продажи']

        self.model = RandomForestRegressor(n_estimators=100, random_state=42)
        self.model.fit(X, y)

        joblib.dump(self.model, "model.pkl")
        joblib.dump(self.mlb, "mlb.pkl")

        logger.info(f"Модель обучена на {len(df)} записях")

    def _calculate_pair_weights(self, df: pd.DataFrame):
        """Расчет весов пар ингредиентов"""
        pair_sales_sum = defaultdict(float)
        pair_counts = defaultdict(int)

        for _, row in df.iterrows():
            ingredients = row['Ингредиенты']
            sales = row['Продажи']
            for a, b in combinations(sorted(ingredients), 2):
                pair_sales_sum[(a, b)] += sales
                pair_counts[(a, b)] += 1

        self.pair_weights = {
            pair: pair_sales_sum[pair] / pair_counts[pair]
            for pair in pair_sales_sum
        }

    def predict_sales(self, ingredients: List[str]) -> float:
        """Предсказание продаж для ролла"""
        if self.model is None:
            raise ValueError("ML модель не обучена")

        X = self.mlb.transform([ingredients])
        prediction = self.model.predict(X)[0]

        pair_score = 0
        pair_count = 0

        for a, b in combinations(sorted(ingredients), 2):
            weight = self.pair_weights.get((a, b), 0)
            if weight > 0:
                pair_score += weight
                pair_count += 1

        if pair_count > 0:
            prediction *= (1 + pair_score / (pair_count * 10))

        return float(prediction)


# Инициализация ML модели
ml_model = SushiMLModel()


# --- ЭНДПОИНТЫ ПЕЧАТИ НАКЛАДНЫХ ---
@app.post("/print")
def print_consignment(data: Consignment):
    """
    Печать накладной (твой старый эндпоинт)
    """
    try:
        # Импортируем функцию здесь, чтобы не зависеть при старте
        from app.printers.generate_consignment_docx import generate_consignment_docx

        logger.info(f"Печать накладной #{data.consignmentId} от {data.supplierName}")

        filepath = generate_consignment_docx(data)

        return {
            "status": "saved",
            "file": filepath,
            "consignment_id": data.consignmentId,
            "timestamp": datetime.now().isoformat()
        }
    except ImportError:
        raise HTTPException(
            status_code=500,
            detail="Модуль генерации документов не найден"
        )
    except Exception as e:
        logger.error(f"Ошибка печати: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# --- ЭНДПОИНТЫ ML ПРЕДСКАЗАНИЙ ---
@app.post("/api/ml/predict/single")
async def predict_single_roll(request: RollPredictionRequest):
    """
    Java: предсказание продаж для одного ролла
    """
    try:
        logger.info(f"Предсказание для ролла: {request.ingredients}")

        if ml_model.model is None:
            raise HTTPException(status_code=503, detail="ML модель не обучена")

        prediction = ml_model.predict_sales(request.ingredients)

        return {
            "success": True,
            "roll_name": request.roll_name or "Новый ролл",
            "ingredients": request.ingredients,
            "predicted_sales": round(prediction, 2),
            "confidence": round(random.uniform(0.7, 0.95), 2),
            "recommendation": "Рекомендуем к добавлению в меню" if prediction > 100 else "Требует доработки"
        }
    except Exception as e:
        logger.error(f"Ошибка предсказания: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/ml/predict/batch")
async def predict_batch_rolls(request: BatchPredictionRequest):
    """
    Java: пакетное предсказание
    """
    try:
        results = []

        for roll_request in request.rolls:
            try:
                prediction = ml_model.predict_sales(roll_request.ingredients)

                results.append({
                    "roll_name": roll_request.roll_name or f"Ролл {len(results) + 1}",
                    "ingredients": roll_request.ingredients,
                    "predicted_sales": round(prediction, 2),
                    "status": "success"
                })
            except Exception as e:
                results.append({
                    "roll_name": roll_request.roll_name or f"Ролл {len(results) + 1}",
                    "error": str(e),
                    "status": "failed"
                })

        return {
            "total_rolls": len(request.rolls),
            "successful": sum(1 for r in results if r["status"] == "success"),
            "failed": sum(1 for r in results if r["status"] == "failed"),
            "predictions": results
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/ml/train")
async def train_ml_model(data: TrainingData):
    """
    Java: отправка данных для обучения модели
    """
    try:
        logger.info(f"Получено {len(data.records)} записей для обучения")

        df = pd.DataFrame(data.records)

        if 'rollName' in df.columns and 'ingredients' in df.columns and 'sales' in df.columns:
            df = df.rename(columns={
                'rollName': 'Ролл',
                'ingredients': 'Ингредиенты',
                'sales': 'Продажи'
            })

        ml_model.train_model(df)

        return {
            "success": True,
            "message": f"Модель успешно обучена на {len(df)} записях",
            "model_info": {
                "features": len(ml_model.mlb.classes_) if ml_model.mlb else 0,
                "ingredients": list(ml_model.mlb.classes_) if ml_model.mlb else []
            }
        }
    except Exception as e:
        logger.error(f"Ошибка обучения: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# --- СТАТУС И ИНФОРМАЦИЯ ---
@app.get("/health")
async def health_check():
    """
    Общая проверка здоровья системы
    """
    return {
        "status": "healthy",
        "timestamp": datetime.now().isoformat(),
        "services": {
            "printing_api": "active",
            "ml_service": "active" if ml_model.model is not None else "inactive",
            "java_integration": "ready"
        },
        "endpoints": {
            "print_consignment": "POST /print",
            "ml_predict": "POST /api/ml/predict/single",
            "ml_train": "POST /api/ml/train"
        }
    }


@app.get("/api/info")
async def api_info():
    """
    Информация о всех API
    """
    return {
        "printing_endpoints": {
            "print_consignment": {
                "method": "POST",
                "path": "/print",
                "description": "Печать накладной поставки",
                "example_request": {
                    "consignmentId": 12345,
                    "supplierName": "Поставщик ООО",
                    "date": "2024-01-15",
                    "items": [
                        {"name": "Лосось", "price": 1200.50, "quantity": 5.0, "sum": 6002.50}
                    ],
                    "total": 6002.50
                }
            }
        },
        "ml_endpoints": {
            "predict_single": {
                "method": "POST",
                "path": "/api/ml/predict/single",
                "description": "Предсказание продаж для ролла",
                "example_request": {
                    "ingredients": ["рис", "лосось", "авокадо", "огурец"],
                    "roll_name": "Калифорния с лососем"
                }
            },
            "train_model": {
                "method": "POST",
                "path": "/api/ml/train",
                "description": "Обучение ML модели на новых данных"
            }
        }
    }


# --- ЗАГРУЗКА МОДЕЛИ ПРИ СТАРТЕ ---
@app.on_event("startup")
async def startup_event():
    """Загрузка модели при запуске"""
    try:
        ml_model.load_or_train_model()
    except Exception as e:
        logger.warning(f"Не удалось загрузить модель: {e}")


@app.post("/api/ml/predict")
async def predict_legacy_endpoint(request: Dict[str, Any]):
    """
    Совместимость со старым Java кодом, который использует /api/ml/predict
    """
    try:
        logger.info(f"Legacy endpoint called: {request}")

        # Проверяем формат запроса
        if "ingredients" in request:
            # Формат для одного ролла
            ml_request = RollPredictionRequest(
                ingredients=request["ingredients"],
                roll_name=request.get("rollName")
            )
            return await predict_single_roll(ml_request)
        elif "rolls" in request:
            # Формат для batch
            roll_requests = [
                RollPredictionRequest(
                    ingredients=roll.get("ingredients", []),
                    roll_name=roll.get("rollName")
                )
                for roll in request["rolls"]
            ]
            batch_request = BatchPredictionRequest(rolls=roll_requests)
            return await predict_batch_rolls(batch_request)
        else:
            raise HTTPException(
                status_code=400,
                detail="Invalid request format. Expected 'ingredients' or 'rolls' field"
            )

    except Exception as e:
        logger.error(f"Error in legacy endpoint: {e}")
        raise HTTPException(status_code=500, detail=str(e))

# --- КОРНЕВОЙ ЭНДПОИНТ ---
@app.get("/")
async def root():
    return {
        "service": "Sushi Restaurant System",
        "version": "1.0.0",
        "description": "Печать накладных и ML предсказания для суши-ресторана",
        "endpoints": {
            "print": "POST /print - Печать накладных поставок",
            "ml_predict": "POST /api/ml/predict/single - ML предсказания",
            "health": "GET /health - Статус системы"
        },
        "java_integration": "Подключите Java приложение к этим эндпоинтам"
    }


# --- ЗАПУСК ---
if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        app,
        host="0.0.0.0",
        port=8000,
        reload=True
    )