from typing import Any

import httpx
from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import JSONResponse

from app.config import get_settings

router = APIRouter(prefix="/auth", tags=["auth"])


async def forward_to_auth_service(
    action: str,
    payload: dict[str, Any],
) -> JSONResponse:
    settings = get_settings()
    url = f"{settings.auth_service_url.rstrip('/')}/auth/{action}"

    try:
        async with httpx.AsyncClient(
            timeout=settings.http_timeout_seconds
        ) as client:
            response = await client.post(url, json=payload)
    except httpx.RequestError:
        raise HTTPException(
            status_code=503,
            detail="Authentication service unavailable",
        )

    try:
        response_body = response.json()
    except ValueError:
        raise HTTPException(
            status_code=502,
            detail="Invalid response from authentication service",
        )

    return JSONResponse(
        status_code=response.status_code,
        content=response_body,
    )


@router.post("/register")
async def register(payload: dict[str, Any]):
    return await forward_to_auth_service("register", payload)


@router.post("/login")
async def login(payload: dict[str, Any]):
    return await forward_to_auth_service("login", payload)

@router.get("/me")
async def me(request: Request):
    settings = get_settings()
    url = f"{settings.auth_service_url.rstrip('/')}/auth/me"

    # Le BFF ne vérifie pas le token : le service auth s'en charge
    authorization = request.headers.get("authorization")
    headers = {"Authorization": authorization} if authorization else {}

    try:
        async with httpx.AsyncClient(
            timeout=settings.http_timeout_seconds
        ) as client:
            response = await client.get(url, headers=headers)
    except httpx.RequestError:
        raise HTTPException(
            status_code=503,
            detail="Authentication service unavailable",
        )

    try:
        response_body = response.json()
    except ValueError:
        raise HTTPException(
            status_code=502,
            detail="Invalid response from authentication service",
        )

    return JSONResponse(
        status_code=response.status_code,
        content=response_body,
    )