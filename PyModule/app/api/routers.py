# app/routers.py
import random

from fastapi import APIRouter, HTTPException
from typing import List

from app.models import (
    PredictionRequest, PredictionResponse,
    OptimizationRequest, OptimizationResponse,
    IngredientDTO, SalesRecordDTO
)
from app.service import ml_service

router = APIRouter()


@router.get("/health")
async def health_check():
    """Проверка здоровья сервиса"""
    return {
        "status": "healthy",
        "service": "ml-predictor",
        "version": "1.0.0"
    }


@router.get("/ingredients", response_model=List[IngredientDTO])
async def get_ingredients():
    """Получить список всех ингредиентов"""
    try:
        ingredients = ml_service.get_ingredients()
        return ingredients
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/ingredients/popular")
async def get_popular_ingredients(days: int = 30, limit: int = 10):
    """Получить популярные ингредиенты"""
    try:
        popular = ml_service.get_popular_ingredients(days, limit)
        return {"ingredients": popular}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/predict", response_model=PredictionResponse)
async def predict_sales(request: PredictionRequest):
    """Предсказать продажи для ролла"""
    try:
        result = ml_service.predict_sales(request.ingredients)

        return PredictionResponse(
            predictedSales=result['predictedSales'],
            confidenceScore=result['confidenceScore'],
            estimatedCost=result['estimatedCost'],
            estimatedProfit=result['estimatedProfit'],
            modelVersion="simple-1.0"
        )
    except Exception as e:
        return PredictionResponse(
            predictedSales=0,
            confidenceScore=0,
            estimatedCost=0,
            estimatedProfit=0,
            errorMessage=str(e)
        )


@router.post("/optimize", response_model=OptimizationResponse)
async def optimize_rolls(request: OptimizationRequest):
    """Оптимизировать состав роллов"""
    try:
        results = ml_service.optimize_roll(request.constraints)

        optimized_rolls = []
        for result in results:
            optimized_rolls.append({
                "name": result['name'],
                "ingredients": result['ingredients'],
                "cost": result['cost'],
                "predictedSales": result['predictedSales'],
                "estimatedProfit": result['estimatedProfit'],
                "profitMargin": result['profitMargin'],
                "score": result['score'],
                "explanation": result['explanation']
            })

        return OptimizationResponse(
            optimizedRolls=optimized_rolls,
            status="success"
        )
    except Exception as e:
        return OptimizationResponse(
            optimizedRolls=[],
            status="error",
            errorMessage=str(e)
        )


@router.get("/sales/mock")
async def get_mock_sales(start_date: str, end_date: str, limit: int = 100):
    """Мок данные о продажах (для тестирования)"""
    try:
        # Генерируем мок данные
        mock_sales = []
        base_date = "2024-01-01"

        for i in range(min(limit, 50)):
            mock_sales.append({
                "rollId": f"roll_{i + 1}",
                "rollName": f"Ролл {i + 1}",
                "ingredients": ["рис", "лосось", "авокадо"][: (i % 3) + 1],
                "saleDate": base_date,
                "quantity": random.randint(5, 50),
                "totalAmount": random.randint(1000, 5000)
            })

        return {"sales": mock_sales, "count": len(mock_sales)}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/menu/mock")
async def get_mock_menu():
    """Мок меню (для тестирования)"""
    menu_items = [
        {
            "id": "1",
            "name": "Калифорния",
            "ingredients": ["рис", "краб", "авокадо", "огурец", "икра"],
            "price": 350,
            "category": "классические"
        },
        {
            "id": "2",
            "name": "Филадельфия",
            "ingredients": ["рис", "лосось", "сыр", "авокадо"],
            "price": 420,
            "category": "премиум"
        },
        {
            "id": "3",
            "name": "Дракон",
            "ingredients": ["рис", "угорь", "авокадо", "соус унаги"],
            "price": 480,
            "category": "премиум"
        },
        {
            "id": "4",
            "name": "Аляска",
            "ingredients": ["рис", "лосось", "авокадо", "огурец"],
            "price": 380,
            "category": "классические"
        }
    ]

    return {"menu": menu_items}