from typing import Annotated, Any

import httpx
from fastapi import APIRouter, HTTPException, Path, Query, Request
from fastapi.responses import JSONResponse

from app.config import get_settings

router = APIRouter(prefix="/reservations", tags=["reservations"])

# Reservation id: MongoDB ObjectId (24 hexadecimal characters)
ReservationId = Annotated[str, Path(pattern=r"^[a-fA-F0-9]{24}$")]


async def forward_to_reservations_service(
    request: Request,
    method: str,
    path: str = "",
    payload: dict[str, Any] | None = None,
) -> JSONResponse:
    settings = get_settings()
    url = f"{settings.reservations_service_url.rstrip('/')}/api/reservations{path}"

    # The BFF does not check the token: the reservations service does.
    headers: dict[str, str] = {}
    authorization = request.headers.get("authorization")
    if authorization:
        headers["Authorization"] = authorization

    try:
        async with httpx.AsyncClient(
            timeout=settings.http_timeout_seconds
        ) as client:
            response = await client.request(
                method, url, headers=headers, json=payload
            )
    except httpx.RequestError:
        raise HTTPException(
            status_code=503,
            detail="Reservations service unavailable",
        )

    try:
        response_body = response.json()
    except ValueError:
        raise HTTPException(
            status_code=502,
            detail="Invalid response from reservations service",
        )

    return JSONResponse(
        status_code=response.status_code,
        content=response_body,
    )


@router.get("")
async def list_reservations(request: Request):
    return await forward_to_reservations_service(request, "GET")

@router.get("/disponibilites")
async def get_disponibilites(
    request: Request,
    borne_id: str = Query(alias="borneId"),
    date: str = Query(pattern=r"^\d{4}-\d{2}-\d{2}$"),
):
    return await forward_to_reservations_service(
        request, "GET", f"/disponibilites?borneId={borne_id}&date={date}"
    )

@router.get("/{reservation_id}")
async def get_reservation(request: Request, reservation_id: ReservationId):
    return await forward_to_reservations_service(
        request, "GET", f"/{reservation_id}"
    )


@router.post("")
async def create_reservation(request: Request, payload: dict[str, Any]):
    return await forward_to_reservations_service(
        request, "POST", payload=payload
    )


@router.patch("/{reservation_id}/annulation")
async def cancel_reservation(request: Request, reservation_id: ReservationId):
    return await forward_to_reservations_service(
        request, "PATCH", f"/{reservation_id}/annulation"
    )