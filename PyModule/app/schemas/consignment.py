from pydantic import BaseModel
from typing import List


class Item(BaseModel):
    name: str
    price: float
    quantity: float
    sum: float


class Consignment(BaseModel):
    consignmentId: int
    supplierName: str
    date: str
    items: List[Item]
    total: float
