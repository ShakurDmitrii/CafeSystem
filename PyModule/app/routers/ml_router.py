from __future__ import annotations

import logging
from datetime import date as Date
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel, ConfigDict, Field, model_validator
from starlette.concurrency import run_in_threadpool

from app.security import require_service_token
from app.services import service
from app.services.dish_generator import generate_new_dish, optimize_rolls


logger = logging.getLogger(__name__)
router = APIRouter(
    prefix="/api/ml",
    tags=["ML"],
    dependencies=[Depends(require_service_token)],
)


class StrictRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")


class MLRequest(StrictRequest):
    ingredients: list[str] = Field(min_length=1, max_length=100)
    date: Date | None = None


class BatchMLRequest(StrictRequest):
    rolls: list[MLRequest] = Field(min_length=1, max_length=500)


def _validate_algorithm_boundary(constraints: dict[str, Any]) -> dict[str, Any]:
    integer_limits = {
        "populationSize": 500,
        "generations": 200,
        "numResults": 50,
        "minIngredients": 100,
        "maxIngredients": 100,
    }
    for key, maximum in integer_limits.items():
        value = constraints.get(key)
        if value is None:
            continue
        try:
            numeric_value = int(value)
        except (TypeError, ValueError) as exc:
            raise ValueError(f"{key} должен быть целым числом") from exc
        if numeric_value > maximum:
            raise ValueError(f"{key} не может быть больше {maximum}")
    return constraints


class GenerateDishRequest(StrictRequest):
    salesRecords: list[dict[str, Any]] = Field(max_length=10_000)
    menuItems: list[dict[str, Any]] = Field(max_length=2_000)
    ingredients: list[dict[str, Any]] = Field(min_length=1, max_length=5_000)
    constraints: dict[str, Any] = Field(default_factory=dict)

    @model_validator(mode="after")
    def validate_constraints(self) -> "GenerateDishRequest":
        _validate_algorithm_boundary(self.constraints)
        return self


class OptimizeRequest(StrictRequest):
    constraints: dict[str, Any] = Field(default_factory=dict)
    ingredients: list[dict[str, Any]] = Field(default_factory=list, max_length=5_000)
    menuItems: list[dict[str, Any]] = Field(default_factory=list, max_length=2_000)
    salesRecords: list[dict[str, Any]] = Field(default_factory=list, max_length=10_000)

    @model_validator(mode="after")
    def validate_constraints(self) -> "OptimizeRequest":
        _validate_algorithm_boundary(self.constraints)
        return self


class TrainingRecord(StrictRequest):
    ingredients: list[str] = Field(min_length=1, max_length=100)
    sales: float = Field(ge=0)
    date: Date
    rollName: str | None = None


class TrainingRequest(StrictRequest):
    records: list[TrainingRecord] = Field(min_length=10, max_length=100_000)


def _prediction_metadata() -> tuple[float, str | None]:
    info = service.get_model_info()
    return service.get_confidence_score(), info.get("modelVersion")


@router.post("/predict")
async def predict_single_endpoint(request: MLRequest) -> dict[str, Any]:
    try:
        prediction = await run_in_threadpool(
            service.predict_single,
            request.ingredients,
            request.date.isoformat() if request.date else None,
        )
        confidence, version = _prediction_metadata()
        return {
            "predictedSales": float(prediction),
            "ingredients": request.ingredients,
            "confidenceScore": confidence,
            "modelVersion": version,
        }
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except Exception as exc:
        logger.exception("Prediction failed")
        raise HTTPException(
            status_code=500,
            detail="Внутренняя ошибка прогнозирования",
        ) from exc


@router.post("/predict/batch")
async def predict_batch_endpoint(request: BatchMLRequest) -> dict[str, Any]:
    try:
        rolls = [
            {
                "ingredients": item.ingredients,
                "date": item.date.isoformat() if item.date else None,
            }
            for item in request.rolls
        ]
        results = await run_in_threadpool(service.predict_batch, rolls)
        confidence, version = _prediction_metadata()
        normalized = []
        for result in results:
            if "error" in result:
                normalized.append(result)
            else:
                normalized.append(
                    {
                        "ingredients": result.get("ingredients"),
                        "predictedSales": result.get("predicted_sales"),
                        "confidenceScore": confidence,
                        "modelVersion": version,
                    }
                )
        return {"results": normalized, "modelVersion": version}
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except Exception as exc:
        logger.exception("Batch prediction failed")
        raise HTTPException(
            status_code=500,
            detail="Внутренняя ошибка пакетного прогнозирования",
        ) from exc


@router.post("/train")
async def train_endpoint(data: TrainingRequest) -> dict[str, Any]:
    try:
        records = [
            record.model_dump(mode="json", exclude_none=True)
            for record in data.records
        ]
        return await run_in_threadpool(service.train_model, records)
    except service.TrainingInProgressError as exc:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except Exception as exc:
        logger.exception("Model training failed")
        raise HTTPException(
            status_code=500,
            detail="Внутренняя ошибка обучения модели",
        ) from exc


@router.get("/health")
async def health_endpoint() -> dict[str, Any]:
    info = service.get_model_info()
    return {
        "status": "ready" if info["modelLoaded"] else "untrained",
        "modelLoaded": info["modelLoaded"],
    }


@router.get("/info")
async def model_info_endpoint() -> dict[str, Any]:
    return service.get_model_info()


@router.get("/insights/popular-pairs")
async def popular_pairs_endpoint(
    limit: int = Query(default=10, ge=1, le=50),
) -> list[str]:
    return service.get_popular_ingredient_pairs(limit)


@router.post("/generate-dish")
async def generate_dish_endpoint(request: GenerateDishRequest) -> dict[str, Any]:
    try:
        return await run_in_threadpool(
            generate_new_dish,
            request.salesRecords,
            request.menuItems,
            request.ingredients,
            request.constraints,
        )
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except Exception as exc:
        logger.exception("Dish generation failed")
        raise HTTPException(
            status_code=500,
            detail="Внутренняя ошибка генерации блюда",
        ) from exc


@router.post("/optimize")
async def optimize_endpoint(request: OptimizeRequest) -> dict[str, Any]:
    try:
        return await run_in_threadpool(
            optimize_rolls,
            request.constraints,
            request.ingredients,
            request.menuItems,
            request.salesRecords,
        )
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except Exception as exc:
        logger.exception("Roll optimization failed")
        raise HTTPException(
            status_code=500,
            detail="Внутренняя ошибка оптимизации",
        ) from exc
