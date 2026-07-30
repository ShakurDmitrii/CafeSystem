import logging
from datetime import datetime, timedelta
from typing import Any

import numpy as np
import pandas as pd


logger = logging.getLogger(__name__)
RANGE_DAYS = {
    "day": 1,
    "week": 7,
    "month": 30,
    "quarter": 90,
    "year": 365,
}


def _first_existing_column(frame: pd.DataFrame, candidates: list[str]) -> str | None:
    return next((name for name in candidates if name in frame.columns), None)


def process_sales_data(sales_data: list[dict[str, Any]]) -> pd.DataFrame:
    if not sales_data:
        return pd.DataFrame()

    frame = pd.DataFrame.from_records(sales_data)
    input_rows = len(frame)
    date_column = _first_existing_column(
        frame,
        ["saleDate", "date", "createdAt", "docDate", "sale_date"],
    )
    quantity_column = _first_existing_column(frame, ["quantity", "qty", "count"])
    amount_column = _first_existing_column(
        frame,
        ["totalAmount", "amount", "lineTotal", "sum"],
    )
    unit_cost_column = _first_existing_column(
        frame,
        ["unitCost", "firstCost", "costPerUnit"],
    )
    total_cost_column = _first_existing_column(frame, ["totalCost", "cost"])

    if date_column is None or quantity_column is None:
        raise ValueError("Продажи должны содержать дату и количество")

    frame = frame.copy()
    frame["saleDate"] = pd.to_datetime(frame[date_column], errors="coerce")
    if getattr(frame["saleDate"].dt, "tz", None) is not None:
        frame["saleDate"] = frame["saleDate"].dt.tz_localize(None)
    frame["quantity"] = pd.to_numeric(frame[quantity_column], errors="coerce")

    if amount_column is not None:
        frame["totalAmount"] = pd.to_numeric(frame[amount_column], errors="coerce")
    elif "pricePerUnit" in frame.columns:
        unit_price = pd.to_numeric(frame["pricePerUnit"], errors="coerce")
        frame["totalAmount"] = frame["quantity"] * unit_price
    else:
        raise ValueError("Продажи должны содержать сумму или цену за единицу")

    if total_cost_column is not None:
        frame["cost"] = pd.to_numeric(frame[total_cost_column], errors="coerce")
    elif unit_cost_column is not None:
        unit_cost = pd.to_numeric(frame[unit_cost_column], errors="coerce")
        frame["cost"] = frame["quantity"] * unit_cost
    else:
        frame["cost"] = np.nan

    valid_rows = (
        frame["saleDate"].notna()
        & frame["quantity"].notna()
        & frame["totalAmount"].notna()
        & np.isfinite(frame["quantity"])
        & np.isfinite(frame["totalAmount"])
        & (frame["quantity"] > 0)
        & (frame["totalAmount"] >= 0)
    )
    frame = frame.loc[valid_rows].copy()
    frame["profit"] = frame["totalAmount"] - frame["cost"]
    frame["margin"] = np.where(
        (frame["totalAmount"] > 0) & frame["profit"].notna(),
        frame["profit"] / frame["totalAmount"] * 100,
        np.nan,
    )

    frame.attrs["data_quality"] = {
        "inputRows": input_rows,
        "validRows": len(frame),
        "rejectedRows": input_rows - len(frame),
        "costCoverage": (
            round(float(frame["cost"].notna().mean()), 4) if len(frame) else 0.0
        ),
    }
    if input_rows != len(frame):
        logger.warning(
            "Rejected %s invalid sales records out of %s",
            input_rows - len(frame),
            input_rows,
        )
    return frame


def filter_by_time_range(
    frame: pd.DataFrame,
    time_range: str,
    *,
    previous: bool = False,
    now: datetime | None = None,
) -> pd.DataFrame:
    if frame.empty:
        return frame.copy()
    days = RANGE_DAYS[time_range]
    current_time = now or datetime.now()
    current_start = current_time - timedelta(days=days)
    if previous:
        previous_start = current_time - timedelta(days=days * 2)
        return frame.loc[
            (frame["saleDate"] >= previous_start)
            & (frame["saleDate"] < current_start)
        ].copy()
    return frame.loc[frame["saleDate"] >= current_start].copy()


def _safe_sum(series: pd.Series) -> float:
    value = series.sum(min_count=1)
    return float(value) if pd.notna(value) else 0.0


def calculate_kpi(
    frame: pd.DataFrame,
    time_range: str,
    *,
    model_accuracy: float | None = None,
) -> dict[str, float | int | None]:
    if frame.empty:
        return {
            "total_profit": 0.0,
            "total_sales": 0,
            "profit_change": 0.0,
            "sales_change": 0.0,
            "model_accuracy": model_accuracy,
        }

    current = filter_by_time_range(frame, time_range)
    previous = filter_by_time_range(frame, time_range, previous=True)

    total_profit = _safe_sum(current["profit"])
    total_sales = int(current["quantity"].sum())
    previous_profit = _safe_sum(previous["profit"])
    previous_sales = int(previous["quantity"].sum())

    def percentage_change(current_value: float, previous_value: float) -> float:
        if previous_value == 0:
            return 0.0
        return round((current_value - previous_value) / previous_value * 100, 2)

    return {
        "total_profit": round(total_profit, 2),
        "total_sales": total_sales,
        "profit_change": percentage_change(total_profit, previous_profit),
        "sales_change": percentage_change(total_sales, previous_sales),
        "model_accuracy": (
            round(float(model_accuracy), 4)
            if model_accuracy is not None
            else None
        ),
    }


def analyze_top_rolls(
    frame: pd.DataFrame,
    time_range: str,
    limit: int = 10,
) -> list[dict[str, Any]]:
    current = filter_by_time_range(frame, time_range)
    if current.empty or "rollName" not in current.columns:
        return []

    grouped = (
        current.groupby("rollName", as_index=False, dropna=False)
        .agg(
            quantity=("quantity", "sum"),
            profit=("profit", lambda values: values.sum(min_count=1)),
            revenue=("totalAmount", "sum"),
        )
        .sort_values("quantity", ascending=False)
        .head(limit)
        .copy()
    )
    grouped["profit"] = grouped["profit"].fillna(0.0)
    grouped["margin"] = np.where(
        grouped["revenue"] > 0,
        grouped["profit"] / grouped["revenue"] * 100,
        0.0,
    )
    return [
        {
            "name": str(row["rollName"]),
            "sales": int(row["quantity"]),
            "profit": round(float(row["profit"]), 2),
            "margin": round(float(row["margin"]), 2),
        }
        for row in grouped.to_dict(orient="records")
    ]


def generate_sales_trend(
    frame: pd.DataFrame,
    time_range: str,
) -> list[dict[str, Any]]:
    current = filter_by_time_range(frame, time_range)
    if current.empty:
        return []

    daily = (
        current.assign(date_only=current["saleDate"].dt.normalize())
        .groupby("date_only", as_index=False)
        .agg(
            quantity=("quantity", "sum"),
            revenue=("totalAmount", "sum"),
        )
        .sort_values("date_only")
    )
    days_ru = ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"]
    return [
        {
            "date": row["date_only"].date().isoformat(),
            "period": days_ru[row["date_only"].weekday()],
            "sales": int(row["quantity"]),
            "predicted": None,
            "revenue": round(float(row["revenue"]), 2),
        }
        for row in daily.to_dict(orient="records")
    ]


def generate_insights(
    frame: pd.DataFrame,
    top_rolls: list[dict[str, Any]],
    time_range: str,
) -> list[dict[str, str]]:
    current = filter_by_time_range(frame, time_range)
    if current.empty:
        return [
            {
                "type": "warning",
                "title": "Недостаточно данных",
                "description": "За выбранный период нет завершённых продаж",
            }
        ]

    insights = [
        {
            "type": "insight",
            "title": "Продажи за выбранный период",
            "description": f"Продано {int(current['quantity'].sum())} порций",
        }
    ]
    if top_rolls:
        insights.append(
            {
                "type": "opportunity",
                "title": f"Лидер продаж: {top_rolls[0]['name']}",
                "description": f"Продано {top_rolls[0]['sales']} порций",
            }
        )
    return insights
