from fastapi import APIRouter, HTTPException
from datetime import datetime
import logging

from app.schemas.consignment import Consignment

logger = logging.getLogger(__name__)

router = APIRouter(tags=["Consignment"])


@router.post("/print")
def print_consignment(data: Consignment):
    """
    Печать накладной
    """
    try:
        from app.printers.generate_consignment_docx import generate_consignment_docx

        logger.info(
            f"Печать накладной #{data.consignmentId} от {data.supplierName}"
        )

        filepath = generate_consignment_docx(data)

        return {
            "status": "saved",
            "file": filepath,
            "consignment_id": data.consignmentId,
            "timestamp": datetime.now().isoformat()
        }

    except ImportError:
        raise HTTPException(
            status_code=500,
            detail="Модуль генерации документов не найден"
        )

    except Exception as e:
        logger.error(f"Ошибка печати: {e}")
        raise HTTPException(status_code=500, detail=str(e))
