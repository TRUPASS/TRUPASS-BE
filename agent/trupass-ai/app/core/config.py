from functools import lru_cache
from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file='.env', extra='ignore')

    gemini_api_key: str = Field(default='', validation_alias=AliasChoices('GEMINI_API_KEY', 'GOOGLE_API_KEY'))
    gemini_model: str = 'gemini-2.5-flash-lite'
    internal_api_key: str = ''
    prompt_ttl_seconds: int = Field(default=600, ge=1)
    prompt_max_entries: int = Field(default=256, ge=1)
    cache_ttl_seconds: int = 600
    cache_max_entries: int = 256


@lru_cache

def get_settings() -> Settings:
    return Settings()
