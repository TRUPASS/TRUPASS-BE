"""환경 변수 설정. 키 이름은 루트 `.env.example`과 같다."""

from functools import lru_cache

from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=None, extra="ignore")

    core_base_url: str = "http://localhost:8080"
    gemini_model: str = ""
    google_api_key: SecretStr | None = None
    core_timeout_seconds: float = 10.0


@lru_cache
def get_settings() -> Settings:
    return Settings()
