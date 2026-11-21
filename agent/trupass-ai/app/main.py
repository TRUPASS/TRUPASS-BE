from fastapi import FastAPI
from app.api.routes import router
from app.core.cache import TTLCache
from app.core.config import get_settings
from app.providers.mock import MockTravelProvider
from app.providers.mock_merchant import MockMerchantProvider
from app.services.analyzer import GeminiAnalyzer
from app.services.budget import BudgetCalculator
from app.services.combiner import CombinationEngine
from app.services.planner import TripPlanner


def create_app() -> FastAPI:
    settings = get_settings()
    providers = {'mock': MockTravelProvider, 'mock-merchant': MockMerchantProvider}
    provider_mode = settings.trupass_provider_mode
    if provider_mode not in providers:
        raise RuntimeError('Unsupported travel provider mode')
    app = FastAPI(title="Trupass AI", version="0.1.0", docs_url="/docs", redoc_url="/redoc", openapi_url="/openapi.json")
    app.state.planner = TripPlanner(
        GeminiAnalyzer(settings.gemini_api_key, settings.gemini_model,
                       TTLCache(settings.cache_ttl_seconds, settings.cache_max_entries)),
        providers[provider_mode](), CombinationEngine(BudgetCalculator()))
    app.include_router(router)

    @app.get('/health')
    def health():
        return {'status': 'ok', 'provider': provider_mode}
    return app


app = create_app()

