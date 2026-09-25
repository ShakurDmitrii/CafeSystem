import hmac
from typing import Annotated

from fastapi import Depends, HTTPException, status
from fastapi.security import APIKeyHeader

from app.config import settings


service_token_header = APIKeyHeader(
    name="X-Service-Token",
    scheme_name="InternalServiceToken",
    description="Shared token for trusted CafeHelp services.",
    auto_error=False,
)
contract_version_header = APIKeyHeader(
    name="X-Contract-Version",
    scheme_name="InternalContractVersion",
    description="Version of the CafeHelp internal API contract.",
    auto_error=False,
)


async def require_service_token(
    provided_token: Annotated[str | None, Depends(service_token_header)],
    provided_version: Annotated[str | None, Depends(contract_version_header)],
) -> None:
    expected_token = settings.INTERNAL_SERVICE_TOKEN.get_secret_value()
    if not provided_token or not hmac.compare_digest(provided_token, expected_token):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Недействительный токен внутреннего сервиса",
        )
    if (
        provided_version is not None
        and provided_version != settings.INTERNAL_API_CONTRACT_VERSION
    ):
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Несовместимая версия внутреннего API-контракта",
        )
