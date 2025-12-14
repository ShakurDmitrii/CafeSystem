from fastapi import APIRouter
from pydantic import BaseModel
from typing import List
from app.services.printer_service import print_consignment

router = APIRouter(prefix="/print", tags=["Print"])

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

@router.post("")
def print_endpoint(data: Consignment):
    print_consignment(data)
    return {"status": "printed"}
