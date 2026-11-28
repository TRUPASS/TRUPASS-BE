from fastapi import FastAPI

from app.api.routes import router
from app.core.cache import TTLCache
from app.core.config import get_settings
from app.core.prompt_memory import PromptMemory
from app.services.analyzer import GeminiAnalyzer
from app.services.recommender import GeminiRecommender


def install_planner(app: FastAPI, analyzer_type: type[GeminiAnalyzer] = GeminiAnalyzer,
                    recommender_type: type[GeminiRecommender] = GeminiRecommender) -> None:
    settings = get_settings()
    app.state.prompt_memory = PromptMemory(settings.prompt_ttl_seconds, settings.prompt_max_entries)
    app.state.analyzer = analyzer_type(settings.gemini_api_key, settings.gemini_model,
                                      TTLCache(settings.cache_ttl_seconds, settings.cache_max_entries))
    app.state.recommender = recommender_type(settings.gemini_api_key, settings.gemini_model,
                                             TTLCache(settings.cache_ttl_seconds, settings.cache_max_entries))
    app.include_router(router)
