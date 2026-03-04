from fastapi import APIRouter, HTTPException
import logging
from datetime import datetime
from pydantic import BaseModel

from app.schemas.consignment import Consignment
from app.schemas.order_print import OrderPrintRequest
from app.printers.escpos_printer import EscposPrinter
from app.services.printer_service import (
    print_consignment as print_consignment_service,
    print_order_ticket as print_order_ticket_service,
)

logger = logging.getLogger(__name__)

router = APIRouter(tags=["Consignment"])

class TestTextPrintRequest(BaseModel):
    text: str = "TEST PRINT"
    copies: int = 1


@router.post("/print")
def print_consignment(data: Consignment):
    """
    Печать накладной
    """
    try:
        logger.info(
            f"Печать накладной #{data.consignmentId} от {data.supplierName}"
        )
        result = print_consignment_service(data)
        return result

    except Exception as e:
        logger.error(f"Ошибка печати: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/print/test-text")
def print_test_text(data: TestTextPrintRequest):
    """Печать простого тестового текста на термопринтер."""
    try:
        copies = max(1, min(10, data.copies))
        lines = [
            "=== CAFEHELP TEST ===",
            f"Time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
            f"Text: {data.text}",
            "=====================",
        ]

        printer = EscposPrinter()
        for _ in range(copies):
            printer.print_lines(lines)

        return {
            "status": "printed",
            "mode": "test-text",
            "copies": copies,
            "text": data.text,
        }
    except Exception as e:
        logger.error(f"Ошибка тестовой печати: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/print/order")
def print_order_ticket(data: OrderPrintRequest):
    """Печать кухонного чека заказа: сначала номер, потом состав/сумма."""
    try:
        result = print_order_ticket_service(data)
        return result
    except Exception as e:
        logger.error(f"Ошибка печати заказа: {e}")
        raise HTTPException(status_code=500, detail=str(e))
