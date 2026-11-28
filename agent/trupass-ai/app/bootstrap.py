import importlib.util
import logging
from typing import Any

from fastapi import FastAPI

from app.api.routes import router
from app.core.cache import TTLCache
from app.core.config import Settings, get_settings
from app.core.prompt_memory import PromptMemory
from app.services.analyzer import GeminiAnalyzer
from app.services.claude import ClaudeAnalyzer, ClaudeRecommender
from app.services.fallback import FallbackAnalyzer, FallbackRecommender
from app.services.recommender import GeminiRecommender

logger = logging.getLogger(__name__)


def _entries(settings: Settings, gemini_type: type, claude_type: type, cache: TTLCache) -> list[tuple[str, Any]]:
    """LLM_PROVIDERS 순서대로 (이름, 구현) 목록을 만든다.

    같은 공급자 안에서는 모델 하나에 키를 차례로 붙인다. 키 하나의 크레딧이 떨어져도
    같은 모델의 다음 키가 먼저 시도된다. 이름에는 키 번호만 넣고 키 값은 넣지 않는다.
    """
    entries: list[tuple[str, Any]] = []
    for provider in settings.provider_order():
        keys = settings.keys_for(provider)
        models = settings.models_for(provider)
        if not keys:
            logger.warning('LLM provider %s has no API key, skipped', provider)
            continue
        if provider == 'anthropic' and importlib.util.find_spec('anthropic') is None:
            logger.error('LLM provider anthropic needs the anthropic package, skipped')
            continue
        for model in models:
            for index, key in enumerate(keys, start=1):
                if provider == 'anthropic':
                    instance = claude_type(key, model, cache, effort=settings.anthropic_effort,
                                           timeout=settings.llm_timeout_seconds)
                else:
                    instance = gemini_type(key, model, cache)
                entries.append((f'{provider}:{model}#{index}', instance))
    return entries


def _chain(settings: Settings, entries: list[tuple[str, Any]], wrapper: type, fallback: Any) -> Any:
    if not entries:
        # 키가 하나도 없으면 예전처럼 Gemini 하나를 둔다. 호출할 때 "키 없음" 오류가 난다.
        return fallback
    if len(entries) == 1:
        # 하나뿐이면 래퍼 없이 그대로 둔다. 기존 테스트가 이 객체의 속성을 직접 바꾼다.
        return entries[0][1]
    return wrapper(entries, settings.llm_cooldown_seconds)


def install_planner(app: FastAPI, analyzer_type: type[GeminiAnalyzer] = GeminiAnalyzer,
                    recommender_type: type[GeminiRecommender] = GeminiRecommender) -> None:
    settings = get_settings()
    app.state.prompt_memory = PromptMemory(settings.prompt_ttl_seconds, settings.prompt_max_entries)

    analyze_cache = TTLCache(settings.cache_ttl_seconds, settings.cache_max_entries)
    analyzers = _entries(settings, analyzer_type, ClaudeAnalyzer, analyze_cache)
    app.state.analyzer = _chain(settings, analyzers, FallbackAnalyzer,
                                analyzer_type(settings.gemini_api_key, settings.gemini_model, analyze_cache))

    recommend_cache = TTLCache(settings.cache_ttl_seconds, settings.cache_max_entries)
    recommenders = _entries(settings, recommender_type, ClaudeRecommender, recommend_cache)
    app.state.recommender = _chain(settings, recommenders, FallbackRecommender,
                                   recommender_type(settings.gemini_api_key, settings.gemini_model, recommend_cache))

    logger.info('LLM order: %s', ', '.join(label for label, _ in analyzers) or 'gemini (no key)')
    app.include_router(router)
