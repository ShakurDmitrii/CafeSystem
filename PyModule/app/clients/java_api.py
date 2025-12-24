import logging
import aiohttp
from typing import Optional, Dict, Any

logger = logging.getLogger(__name__)

JAVA_API_URL = "http://localhost:8080"


class JavaApiClient:
    def __init__(self, base_url: str = JAVA_API_URL):
        self.base_url = base_url
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
        timeout: int = 10
    ) -> Optional[Any]:
        if not self.session:
            raise RuntimeError("JavaApiClient not initialized")

        url = f"{self.base_url}{endpoint}"

        try:
            async with self.session.get(url, params=params, timeout=timeout) as response:
                if response.status == 200:
                    return await response.json()

                logger.error(f"Java API error {response.status}: {url}")
                return None

        except Exception as e:
            logger.error(f"Java API request failed: {e}")
            return None
