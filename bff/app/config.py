from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    app_name: str = "SmartCharge BFF"
    stations_service_url: str = "http://localhost:3001"
    cors_origins: list[str] = ["http://localhost:4200"]
    http_timeout_seconds: float = 5.0
    auth_service_url: str = "http://localhost:3003"
    reservations_service_url: str = "http://localhost:3002"


@lru_cache
def get_settings() -> Settings:
    return Settings()