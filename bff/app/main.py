from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.routers.auth import router as auth_router

from app.config import get_settings
from app.routers.bornes import router as bornes_router

settings = get_settings()

app = FastAPI(title=settings.app_name, version="0.1.0")
app.include_router(auth_router, prefix="/api")

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health", tags=["infra"])
async def health():
    return {"status": "ok", "service": "bff"}

app.include_router(bornes_router)