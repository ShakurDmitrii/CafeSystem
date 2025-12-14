from fastapi import FastAPI
from pydantic import BaseModel
from typing import List
from app.printers.generate_consignment_docx import generate_consignment_docx

app = FastAPI()

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

@app.post("/print")
def print_consignment(data: Consignment):
    # Генерация docx через существующую функцию
    filepath = generate_consignment_docx(data)
    return {"status": "saved", "file": filepath}
