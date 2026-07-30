import logging
import aiohttp
from typing import Optional, Dict, Any

from app.config import settings

logger = logging.getLogger(__name__)


class JavaApiError(RuntimeError):
    def __init__(self, status: int, message: str):
        super().__init__(message)
        self.status = status


class JavaApiClient:
    def __init__(self, base_url: str | None = None):
        self.base_url = (base_url or settings.JAVA_API_URL).rstrip("/")
        self.session: aiohttp.ClientSession | None = None

    async def connect(self):
        if not self.session:
            self.session = aiohttp.ClientSession()

    async def close(self):
        if self.session:
            await self.session.close()

    async def get(
        self,
        endpoint: str,
        params: Optional[Dict[str, Any]] = None,
        headers: Optional[Dict[str, str]] = None,
        timeout: int = 10
    ) -> Any:
        if not self.session:
            raise RuntimeError("JavaApiClient not initialized")

        url = f"{self.base_url}{endpoint}"
        request_headers = dict(headers or {})
        request_headers["X-Service-Token"] = (
            settings.INTERNAL_SERVICE_TOKEN.get_secret_value()
        )

        try:
            async with self.session.get(
                url,
                params=params,
                headers=request_headers,
                timeout=aiohttp.ClientTimeout(total=timeout),
            ) as response:
                if response.status == 200:
                    return await response.json()

                logger.error(f"Java API error {response.status}: {url}")
                raise JavaApiError(
                    response.status,
                    f"Java API returned HTTP {response.status}",
                )

        except JavaApiError:
            raise
        except Exception as e:
            logger.error(f"Java API request failed: {e}")
            raise JavaApiError(502, "Java API unavailable") from e
