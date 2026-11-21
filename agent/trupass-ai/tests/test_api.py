from fastapi.testclient import TestClient
from app.core.config import get_settings
from app.main import create_app


def test_health_and_auth(monkeypatch):
    monkeypatch.setenv('INTERNAL_API_KEY', 'test-secret-token')
    get_settings.cache_clear()
    client = TestClient(create_app())
    assert client.get('/health').status_code == 200
    payload = {'budget_krw': 100000, 'transport_krw': 40000, 'hotel_krw': 50000,
               'estimated_local_transport_krw': 15000, 'estimated_other_krw': 5000}
    assert client.post('/api/v1/trips/reprice', json=payload).status_code == 401
    resp = client.post('/api/v1/trips/reprice', json=payload,
                       headers={'X-Internal-API-Key': 'test-secret-token'})
    assert resp.status_code == 200
    assert resp.json()['over_budget_krw'] == 10000
    assert resp.json()['total_krw'] == 110000
    get_settings.cache_clear()


def test_plan_endpoint_without_gemini(monkeypatch):
    from datetime import date, timedelta
    from app.domain.models import TripRequirements
    from app.services.planner import TripPlanner
    from app.providers.mock import MockTravelProvider
    from app.services.combiner import CombinationEngine
    from app.services.budget import BudgetCalculator

    class FakeAnalyzer:
        def analyze(self, request):
            dep = date.today() + timedelta(days=20)
            return (TripRequirements(origin='서울', destination='부산',
                                    departure_date=dep, return_date=dep+timedelta(days=2),
                                    budget_krw=900000), False, 0, 0)

    monkeypatch.setenv('INTERNAL_API_KEY', 'test-secret-token')
    get_settings.cache_clear()
    app = create_app()
    app.state.planner = TripPlanner(FakeAnalyzer(), MockTravelProvider(),
                                    CombinationEngine(BudgetCalculator()))
    resp = TestClient(app).post('/api/v1/trips/plan',
         json={'prompt': '내년에 서울에서 부산으로 여행하고 싶어요'},
         headers={'X-Internal-API-Key': 'test-secret-token'})
    assert resp.status_code == 200, resp.text
    assert len(resp.json()['combinations']) == 3
    assert resp.json()['gemini_called'] is False
    assert resp.json()['quote_status'] == 'mock'
    get_settings.cache_clear()
