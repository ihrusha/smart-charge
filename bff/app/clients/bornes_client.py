
import httpx

from app.config import get_settings


async def get_bornes(
    ville: str | None = None,
    puissance_min: float | None = None,
    type_connecteur: str | None = None,
):
    settings = get_settings()

    params = {
        "ville": ville,
        "puissanceMin": puissance_min,
        "typeConnecteur": type_connecteur,
    }

    params = {
        key: value
        for key, value in params.items()
        if value is not None
    }

    async with httpx.AsyncClient(
        timeout=settings.http_timeout_seconds
    ) as client:

        response = await client.get(
            f"{settings.stations_service_url}/bornes",
            params=params,
        )

        response.raise_for_status()

        return response.json()
