from fastapi import FastAPI, APIRouter
from fastapi.middleware.cors import CORSMiddleware
import logging


from app.routers.consignment import router as consignment_router
from app.clients.java_api import JavaApiClient
from app.routers.analytics import router as analytics_router
from app.routers.ml_router import router as ml_router
from app.services import service
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)




app = FastAPI(title="Sushi Analytics API", version="1.0.0")
# Загружаем модель один раз при старте
service.load_model()


app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000", "http://localhost:8080"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# --- Routers ---
app.include_router(consignment_router)
app.include_router(analytics_router)
app.include_router(ml_router)


# --- Java API client ---
java_client = JavaApiClient()


@app.on_event("startup")
async def startup():
    await java_client.connect()


@app.on_event("shutdown")
async def shutdown():
    await java_client.close()
