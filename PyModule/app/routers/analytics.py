import logging
from datetime import date, datetime, timedelta
from typing import Annotated, Any, Literal

from fastapi import APIRouter, Depends, HTTPException, Query
from starlette.concurrency import run_in_threadpool

from app.clients.java_api import JavaApiClient, JavaApiError
from app.schemas.dashboard import DashboardResponse
from app.security import require_service_token
from app.services import service
from app.services.analytics import (
    RANGE_DAYS,
    analyze_top_rolls,
    calculate_kpi,
    generate_insights,
    generate_sales_trend,
    process_sales_data,
)


logger = logging.getLogger(__name__)
TimeRange = Literal["day", "week", "month", "quarter", "year"]
router = APIRouter(
    prefix="/api/analytics",
    tags=["Analytics"],
    dependencies=[Depends(require_service_token)],
)


def get_java_client() -> JavaApiClient:
    from app.main import java_client

    return java_client


def _build_dashboard(
    sales_data: list[dict[str, Any]],
    time_range: TimeRange,
) -> dict[str, Any]:
    frame = process_sales_data(sales_data)
    model_accuracy = service.get_confidence_score()
    top_rolls = analyze_top_rolls(frame, time_range)
    return {
        "kpi": calculate_kpi(
            frame,
            time_range,
            model_accuracy=model_accuracy,
        ),
        "top_rolls": top_rolls,
        "sales_trend": generate_sales_trend(frame, time_range),
        "insights": generate_insights(frame, top_rolls, time_range),
        "time_range": time_range,
        "generated_at": datetime.now().isoformat(),
        "data_source": "CafeHelp sales database",
        "data_quality": frame.attrs.get(
            "data_quality",
            {
                "inputRows": 0,
                "validRows": 0,
                "rejectedRows": 0,
                "costCoverage": 0.0,
            },
        ),
    }


async def _load_dashboard(
    time_range: TimeRange,
    java_client: JavaApiClient,
    start_date: date | None = None,
    end_date: date | None = None,
) -> dict[str, Any]:
    effective_end = end_date or datetime.now().date()
    effective_start = start_date or (
        effective_end - timedelta(days=RANGE_DAYS[time_range] * 2)
    )
    try:
        sales_data = await java_client.get(
            "/api/ml/data/sales",
            {
                "startDate": effective_start.isoformat(),
                "endDate": effective_end.isoformat(),
                "limit": 10_000,
            },
            timeout=30,
        )
    except JavaApiError as exc:
        status_code = exc.status if exc.status in {401, 403} else 502
        raise HTTPException(
            status_code=status_code,
            detail="Не удалось получить продажи из Java API",
        ) from exc

    if not isinstance(sales_data, list):
        raise HTTPException(
            status_code=502,
            detail="Java API вернул некорректный формат продаж",
        )
    try:
        return await run_in_threadpool(_build_dashboard, sales_data, time_range)
    except ValueError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@router.get("/dashboard", response_model=DashboardResponse)
async def get_dashboard(
    timeRange: Annotated[TimeRange, Query()] = "week",
    refresh: bool = False,
    startDate: date | None = None,
    endDate: date | None = None,
    java_client: JavaApiClient = Depends(get_java_client),
) -> dict[str, Any]:
    del refresh
    return await _load_dashboard(timeRange, java_client, startDate, endDate)


@router.get("/kpi")
async def get_kpi(
    timeRange: Annotated[TimeRange, Query()] = "week",
    java_client: JavaApiClient = Depends(get_java_client),
) -> dict[str, Any]:
    return (await _load_dashboard(timeRange, java_client))["kpi"]


@router.get("/top-rolls")
async def get_top_rolls(
    timeRange: Annotated[TimeRange, Query()] = "week",
    limit: int = Query(default=10, ge=1, le=100),
    java_client: JavaApiClient = Depends(get_java_client),
) -> list[dict[str, Any]]:
    dashboard = await _load_dashboard(timeRange, java_client)
    return dashboard["top_rolls"][:limit]


@router.get("/sales-trend")
async def get_sales_trend(
    timeRange: Annotated[TimeRange, Query()] = "week",
    java_client: JavaApiClient = Depends(get_java_client),
) -> list[dict[str, Any]]:
    return (await _load_dashboard(timeRange, java_client))["sales_trend"]


@router.get("/insights")
async def get_insights(
    timeRange: Annotated[TimeRange, Query()] = "week",
    java_client: JavaApiClient = Depends(get_java_client),
) -> list[dict[str, Any]]:
    return (await _load_dashboard(timeRange, java_client))["insights"]
