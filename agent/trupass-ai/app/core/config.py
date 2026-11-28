from functools import lru_cache
from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


def _split(value: str) -> list[str]:
    return [item.strip() for item in value.split(',') if item.strip()]


class Settings(BaseSettings):
    # docker compose의 env_file은 `KEY=`를 빈 문자열로 넘긴다. 빈 값은 설정 안 한 것으로 보고 기본값을 쓴다.
    model_config = SettingsConfigDict(env_file='.env', extra='ignore', env_ignore_empty=True)

    gemini_api_key: str = Field(default='', validation_alias=AliasChoices('GEMINI_API_KEY', 'GOOGLE_API_KEY'))
    gemini_model: str = 'gemini-2.5-flash-lite'
    internal_api_key: str = ''
    prompt_ttl_seconds: int = Field(default=600, ge=1)
    prompt_max_entries: int = Field(default=256, ge=1)
    cache_ttl_seconds: int = 600
    cache_max_entries: int = 256

    # LLM 여러 개를 순서대로 시도한다. 앞의 것이 실패하면 다음 키·모델로 넘어간다.
    # 비우면 지금처럼 Gemini 하나만 쓴다.
    llm_providers: str = 'gemini'
    # 같은 공급자의 키·모델을 쉼표로 여러 개 넣을 수 있다. 비우면 위의 단일 값을 쓴다.
    gemini_api_keys: str = ''
    gemini_models: str = ''
    anthropic_api_keys: str = Field(default='', validation_alias=AliasChoices('ANTHROPIC_API_KEYS', 'ANTHROPIC_API_KEY'))
    anthropic_models: str = 'claude-opus-5-5'
    anthropic_effort: str = 'low'
    llm_timeout_seconds: float = Field(default=25, gt=0)
    llm_cooldown_seconds: int = Field(default=60, ge=0)

    def provider_order(self) -> list[str]:
        return [name.lower() for name in _split(self.llm_providers)] or ['gemini']

    def keys_for(self, provider: str) -> list[str]:
        if provider == 'gemini':
            return _split(self.gemini_api_keys) or _split(self.gemini_api_key)
        if provider == 'anthropic':
            return _split(self.anthropic_api_keys)
        raise ValueError(f'unknown LLM provider: {provider}')

    def models_for(self, provider: str) -> list[str]:
        if provider == 'gemini':
            return _split(self.gemini_models) or [self.gemini_model]
        if provider == 'anthropic':
            return _split(self.anthropic_models)
        raise ValueError(f'unknown LLM provider: {provider}')


@lru_cache

def get_settings() -> Settings:
    return Settings()
