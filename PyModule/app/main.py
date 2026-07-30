from contextlib import asynccontextmanager
import logging

from fastapi import FastAPI

from app.clients.java_api import JavaApiClient
from app.routers.analytics import router as analytics_router
from app.routers.ml_router import router as ml_router
from app.services import service


logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)
java_client = JavaApiClient()


@asynccontextmanager
async def lifespan(_: FastAPI):
    service.load_model()
    await java_client.connect()
    try:
        yield
    finally:
        await java_client.close()


app = FastAPI(
    title="CafeHelp Analytics API",
    version="2.0.0",
    lifespan=lifespan,
)

app.include_router(analytics_router)
app.include_router(ml_router)


@app.get("/health", tags=["System"])
async def health() -> dict[str, str]:
    """Проверка жизни процесса без проверки готовности ML-модели."""
    return {"status": "ok"}


@app.get("/ready", tags=["System"])
async def readiness() -> dict[str, object]:
    """Проверка готовности API; отсутствие модели отражается отдельно."""
    info = service.get_model_info()
    return {
        "status": "ready",
        "modelLoaded": info["modelLoaded"],
        "javaClientConnected": java_client.session is not None,
    }
