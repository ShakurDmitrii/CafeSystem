from fastapi import APIRouter, HTTPException, Depends
from datetime import datetime
import logging

from app.clients.java_api import JavaApiClient
from app.services.analytics import (
    process_sales_data,
    calculate_kpi,
    analyze_top_rolls,
    generate_sales_trend,
    generate_insights
)
from app.schemas.dashboard import DashboardResponse

logger = logging.getLogger(__name__)

router = APIRouter(
    prefix="/api/analytics",
    tags=["Analytics"]
)


# Dependency для JavaApiClient
def get_java_client() -> JavaApiClient:
    from app.main import java_client
    return java_client


@router.get("/dashboard", response_model=DashboardResponse)
async def get_dashboard(
    timeRange: str = "week",
    java_client: JavaApiClient = Depends(get_java_client)
):
    logger.info(f"Dashboard request started, timeRange={timeRange}")

    # --- Запрос в Java API ---
    sales_data = await java_client.get(
        "/api/ml/data/sales",
        {
            "startDate": "2024-01-01",
            "endDate": datetime.now().date().isoformat(),
            "limit": 1000
        }
    )

    # --- ЛОГИ ДЛЯ ДЕБАГА (ВАЖНО) ---
    if sales_data is None:
        logger.error("Java API returned None")
        raise HTTPException(status_code=502, detail="Java API unavailable")

    logger.info(f"Sales records count: {len(sales_data)}")

    if len(sales_data) > 0:
        logger.info(f"Sales sample (first item): {sales_data[0]}")
        logger.info(f"Sales keys: {list(sales_data[0].keys())}")
    else:
        logger.warning("Sales data is empty list")

    # --- Обработка данных ---
    df = process_sales_data(sales_data)

    logger.info(f"DataFrame columns after processing: {list(df.columns)}")
    logger.info(f"Total quantity sum: {df['quantity'].sum() if 'quantity' in df.columns else 'NO quantity'}")
    logger.info(f"Total profit sum: {df['profit'].sum() if 'profit' in df.columns else 'NO profit'}")

    # --- Аналитика ---
    kpi = calculate_kpi(df, timeRange)
    top_rolls = analyze_top_rolls(df)
    sales_trend = generate_sales_trend(df, timeRange)
    insights = generate_insights(df, top_rolls)

    logger.info("Dashboard response generated successfully")

    return {
        "kpi": kpi,
        "top_rolls": top_rolls,
        "sales_trend": sales_trend,
        "insights": insights,
        "time_range": timeRange,
        "generated_at": datetime.now().isoformat(),
        "data_source": "Real Data from Java API"
    }
