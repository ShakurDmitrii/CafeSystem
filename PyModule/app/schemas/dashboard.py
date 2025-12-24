from pydantic import BaseModel
from typing import List


class KpiData(BaseModel):
    total_profit: float
    total_sales: int
    profit_change: float
    sales_change: float
    model_accuracy: float


class TopRoll(BaseModel):
    name: str
    sales: int
    profit: float
    margin: float


class SalesTrend(BaseModel):
    date: str
    period: str
    sales: int
    predicted: int
    revenue: float


class Insight(BaseModel):
    type: str
    title: str
    description: str


class DashboardResponse(BaseModel):
    kpi: KpiData
    top_rolls: List[TopRoll]
    sales_trend: List[SalesTrend]
    insights: List[Insight]
    time_range: str
    generated_at: str
    data_source: str
