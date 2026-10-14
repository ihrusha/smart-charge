from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException
from app.routers.auth import router as auth_router
from app.routers.reservations import router as reservations_router

from app.config import get_settings
from app.routers.bornes import router as bornes_router

settings = get_settings()

app = FastAPI(title=settings.app_name, version="0.1.0")
app.include_router(auth_router, prefix="/api")
app.include_router(reservations_router, prefix="/api")

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_methods=["*"],
    allow_headers=["*"],
)


# Les services renvoient {"error": "..."} : le BFF fait de même pour ses propres erreurs.
@app.exception_handler(StarletteHTTPException)
async def http_exception_handler(request: Request, exc: StarletteHTTPException):
    return JSONResponse(
        status_code=exc.status_code,
        content={"error": exc.detail},
        headers=getattr(exc, "headers", None),
    )


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    return JSONResponse(
        status_code=422,
        content={"error": "Requête invalide"},
    )


@app.get("/health", tags=["infra"])
async def health():
    return {"status": "ok", "service": "bff"}

app.include_router(bornes_router)