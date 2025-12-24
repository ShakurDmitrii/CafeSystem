# app/routes/ml_router.py
from fastapi import APIRouter, HTTPException
from typing import List

from pydantic import BaseModel

from app.services import service

router = APIRouter(prefix="/api/ml", tags=["ML"])

class MLRequest(BaseModel):
    ingredients: List[str]

class BatchMLRequest(BaseModel):
    rolls: List[MLRequest]

@router.post("/predict")
async def predict_single_endpoint(request: MLRequest):
    """Эндпоинт предсказания для одного ролла"""
    try:
        prediction = service.predict_single(request.ingredients)
        return {
            "predicted_sales": prediction,
            "ingredients": request.ingredients,
            "confidence": 0.85
        }
    except RuntimeError as e:
        raise HTTPException(status_code=503, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/predict/batch")
async def predict_batch_endpoint(request: BatchMLRequest):
    """Пакетное предсказание"""
    try:
        rolls = [r.ingredients for r in request.rolls]
        results = service.predict_batch(rolls)
        return {"results": results}
    except RuntimeError as e:
        raise HTTPException(status_code=503, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/train")
async def train_endpoint(data: dict):
    """Обучение модели"""
    try:
        result = service.train_model(data.get("records", []))
        return result
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/health")
async def health_endpoint():
    """Проверка готовности модели"""
    return {
        "status": "ready",
        "model_loaded": service.model is not None
    }
