from pydantic import BaseModel
from typing import List


class OrderPrintItem(BaseModel):
    name: str
    quantity: float
    price: float
    sum: float


class OrderPrintRequest(BaseModel):
    orderId: int
    createdAt: str | None = None
    items: List[OrderPrintItem]
    total: float
    isDelivery: bool = False
    deliveryCost: float = 0.0
    paymentType: str = "cash"  # cash | transfer | unpaid
    deliveryPhone: str | None = None
    deliveryAddress: str | None = None
