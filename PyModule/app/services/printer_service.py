import os
import logging
import time
from datetime import datetime
from typing import Any, Dict

from app.config import settings
from app.printers.escpos_printer import EscposPrinter
from app.printers.generate_consignment_docx import generate_consignment_docx
from app.templates.consignment_ticket import build_ticket
from app.templates.order_ticket import (
    build_kitchen_ticket,
    build_order_number_ticket,
)
from app.schemas.order_print import OrderPrintRequest

logger = logging.getLogger(__name__)


def _print_via_word_com(filepath: str, printer_name: str | None = None) -> tuple[bool, str]:
    try:
        import pythoncom  # type: ignore
        import win32com.client  # type: ignore
    except Exception as exc:
        return False, f"pywin32 недоступен: {exc}"

    try:
        pythoncom.CoInitialize()
        word = win32com.client.DispatchEx("Word.Application")
        word.Visible = False
        doc = word.Documents.Open(filepath, ReadOnly=True)
        try:
            # Background=False повышает вероятность завершения печати до закрытия.
            if printer_name:
                old_printer = word.ActivePrinter
                word.ActivePrinter = printer_name
                doc.PrintOut(Background=False)
                word.ActivePrinter = old_printer
            else:
                doc.PrintOut(Background=False)
        finally:
            doc.Close(False)
            word.Quit()
        return True, "Печать через Word COM отправлена"
    except Exception as exc:
        return False, f"Ошибка COM-печати: {exc}"


def _print_via_startfile(filepath: str) -> tuple[bool, str]:
    try:
        os.startfile(filepath, "print")
        return True, "Печать через os.startfile отправлена"
    except Exception as exc:
        return False, f"Ошибка startfile-печати: {exc}"


def _print_via_windows(filepath: str) -> tuple[bool, str]:
    if os.name != "nt":
        msg = "Windows печать доступна только на Windows"
        logger.warning(msg)
        return False, msg

    mode = (settings.WINDOWS_PRINT_MODE or "auto").strip().lower()
    printer_name = (settings.PRINTER_NAME or "").strip() or None
    attempts: list[str] = []

    if mode in ("auto", "com"):
        ok, msg = _print_via_word_com(filepath, printer_name)
        attempts.append(msg)
        if ok:
            return True, msg

    if mode in ("auto", "startfile"):
        ok, msg = _print_via_startfile(filepath)
        attempts.append(msg)
        if ok:
            return True, msg

    return False, " | ".join(attempts) if attempts else "Неизвестная ошибка печати"


def print_consignment(data) -> Dict[str, Any]:
    mode = (settings.DEFAULT_PRINTER or "windows").strip().lower()
    timestamp = datetime.now().isoformat()

    if mode == "escpos":
        lines = build_ticket(data)
        EscposPrinter().print_lines(lines)
        return {
            "status": "printed",
            "printer_mode": "escpos",
            "consignment_id": data.consignmentId,
            "timestamp": timestamp,
        }

    filepath = generate_consignment_docx(data)
    printed, print_message = _print_via_windows(filepath)

    return {
        "status": "printed" if printed else "saved",
        "printer_mode": "windows",
        "file": filepath,
        "consignment_id": data.consignmentId,
        "timestamp": timestamp,
        "message": print_message,
        "printer_name": settings.PRINTER_NAME,
        "windows_print_mode": settings.WINDOWS_PRINT_MODE,
    }


def print_order_ticket(data: OrderPrintRequest) -> Dict[str, Any]:
    timestamp = datetime.now().isoformat()

    order_lines = build_order_number_ticket(data)
    kitchen_lines = build_kitchen_ticket(data)

    printer = EscposPrinter()
    printer.print_lines(order_lines)
    time.sleep(5)
    printer.print_lines(kitchen_lines)

    return {
        "status": "printed",
        "printer_mode": "escpos",
        "order_id": data.orderId,
        "timestamp": timestamp,
        "copies": 2,
    }
