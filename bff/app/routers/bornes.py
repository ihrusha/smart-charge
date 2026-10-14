
import httpx

from fastapi import APIRouter, HTTPException, Query

from app.clients.bornes_client import get_bornes

router = APIRouter(prefix="/api/bornes", tags=["bornes"])


@router.get("")
async def rechercher_bornes(
    ville: str | None = None,
    puissance_min: float | None = Query(default=None, ge=0),
    type_connecteur: str | None = None,
):
    try:
        return await get_bornes(
            ville=ville,
            puissance_min=puissance_min,
            type_connecteur=type_connecteur,
        )

    except httpx.HTTPStatusError as exc:
        raise HTTPException(
            status_code=exc.response.status_code,
            detail="Erreur retournée par l'API Bornes",
        ) from exc

    except httpx.RequestError as exc:
        raise HTTPException(
            status_code=502,
            detail="Impossible de contacter l'API Bornes",
        ) from exc
