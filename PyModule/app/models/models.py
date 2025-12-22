# app/models.py
from pydantic import BaseModel
from typing import List, Optional, Dict, Any
from datetime import date

# ==================== Запросы ====================

class PredictionRequest(BaseModel):
    """Запрос на предсказание продаж"""
    ingredients: List[str]
    date: Optional[str] = None

class OptimizationRequest(BaseModel):
    """Запрос на оптимизацию"""
    constraints: Dict[str, Any]
    optimizationType: str = "profit_maximization"

class BatchPredictionRequest(BaseModel):
    """Пакетный запрос на предсказание"""
    rolls: List[Dict[str, Any]]

# ==================== Ответы ====================

class PredictionResponse(BaseModel):
    """Ответ на предсказание"""
    predictedSales: float
    confidenceScore: float
    estimatedCost: float
    estimatedProfit: float
    modelVersion: str = "1.0"
    errorMessage: Optional[str] = None

class OptimizedRoll(BaseModel):
    """Оптимизированный ролл"""
    name: str
    ingredients: List[str]
    cost: float
    predictedSales: float
    estimatedProfit: float
    profitMargin: float
    score: float
    explanation: str

class OptimizationResponse(BaseModel):
    """Ответ на оптимизацию"""
    optimizedRolls: List[OptimizedRoll]
    status: str = "success"
    errorMessage: Optional[str] = None

class IngredientDTO(BaseModel):
    """Ингредиент"""
    name: str
    costPerUnit: Optional[float] = None
    unit: Optional[str] = None
    category: Optional[str] = None

class SalesRecordDTO(BaseModel):
    """Запись о продажах"""
    rollId: str
    rollName: str
    ingredients: List[str]
    saleDate: str
    quantity: int
    totalAmount: float