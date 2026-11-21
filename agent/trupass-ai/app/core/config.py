from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file='.env', extra='ignore')

    gemini_api_key: str = ''
    gemini_model: str = 'gemini-2.5-flash-lite'
    internal_api_key: str = ''
    trupass_provider_mode: str = 'mock'
    cache_ttl_seconds: int = 600
    cache_max_entries: int = 256


@lru_cache

def get_settings() -> Settings:
    return Settings()
