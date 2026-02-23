from fastapi import APIRouter, HTTPException
import logging

from app.schemas.consignment import Consignment
from app.services.printer_service import print_consignment as print_consignment_service

logger = logging.getLogger(__name__)

router = APIRouter(tags=["Consignment"])


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
