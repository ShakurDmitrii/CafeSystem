# app/routes/ml_router.py
from fastapi import APIRouter, HTTPException
from typing import List

from pydantic import BaseModel

from app.services import service
from app.services.dish_generator import generate_new_dish

router = APIRouter(prefix="/api/ml", tags=["ML"])

class MLRequest(BaseModel):
    ingredients: List[str]

class BatchMLRequest(BaseModel):
    rolls: List[MLRequest]


class GenerateDishRequest(BaseModel):
    salesRecords: List[dict]
    menuItems: List[dict]
    ingredients: List[dict]
    constraints: dict = {}

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


@router.post("/generate-dish")
async def generate_dish_endpoint(request: GenerateDishRequest):
    """Генерация нового блюда на основе продаж и техкарт"""
    try:
        return generate_new_dish(
            sales_records=request.salesRecords,
            menu_items=request.menuItems,
            ingredients=request.ingredients,
            constraints=request.constraints,
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
