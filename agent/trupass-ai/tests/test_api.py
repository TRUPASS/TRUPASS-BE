
from fastapi.testclient import TestClient
from app.core.config import get_settings
from app.main import create_app
from app.domain.models import TripRequirements
from app.services.analyzer import SYSTEM_INSTRUCTION
from pydantic import ValidationError
from unittest.mock import Mock
import pytest


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


def test_prompt_edits_are_isolated_and_extraction_is_sparse(monkeypatch):
    monkeypatch.setenv('INTERNAL_API_KEY', 'test')
    get_settings.cache_clear()
    app = create_app()
    with TestClient(app, headers={'X-Internal-API-Key': 'test'}) as client:
        def save(**fields):
            response = client.post('/api/v1/trips/prompts', json=fields)
            assert response.status_code == 200
            return response.json()['prompt_id']

        first = save(prompt='도쿄', accommodation_prompt='역 근처')
        second = save(purpose_prompt='휴식')
        save(prompt_id=first, accommodation_prompt='여행자 평점 4.5 이상',
             purpose_prompt='가장 중요한 건 가족과 대화하는 것. 함께 쉬고 싶어')
        assert client.get(f'/api/v1/trips/prompts/{second}').json()['prompts']['purpose_prompt'] == '휴식'
        fields = client.get(f'/api/v1/trips/prompts/{first}').json()['prompts']
        assert fields['prompt'] == '도쿄' and fields['accommodation_prompt'] == '여행자 평점 4.5 이상'
        conditions = TripRequirements(destination='도쿄', core_travel_goal='가족과 대화', desired_satisfaction='함께 휴식',
                                      min_traveler_rating=4.5, additional_cities=[])
        analyze = Mock(return_value=(conditions, True, 20, 10))
        monkeypatch.setattr(app.state.analyzer, 'analyze', analyze)
        result = client.post('/api/v1/trips/plan', json={'prompt_id': first}).json()
        assert analyze.call_args.args[0].purpose_prompt == fields['purpose_prompt']
        assert result['requirements'] == conditions.model_dump(exclude_none=True)
        assert set(result['requirements']) == {'destination', 'core_travel_goal', 'desired_satisfaction', 'min_traveler_rating'}
        assert 'combinations' not in result
        save(prompt_id=first, accommodation_prompt=None)
        assert 'accommodation_prompt' not in client.get(f'/api/v1/trips/prompts/{first}').json()['prompts']
        assert client.post('/api/v1/trips/prompts', json={}).status_code == 422
        assert client.post('/api/v1/trips/plan', json={'prompt_id': 'ffffffff-ffff-4fff-8fff-ffffffffffff'}).status_code == 404
    get_settings.cache_clear()


def test_rating_range_and_schema_documentation():
    for score in (1, 4.5, 5):
        assert TripRequirements(min_traveler_rating=score).min_traveler_rating == score
    for score in (0.9, 5.1):
        with pytest.raises(ValidationError):
            TripRequirements(min_traveler_rating=score)
    assert TripRequirements().model_dump(exclude_none=True) == {}
    for name in TripRequirements.model_fields:
        assert f'| {name} |' in SYSTEM_INSTRUCTION


def test_export_writes_one_combined_json(monkeypatch):
    monkeypatch.setenv('INTERNAL_API_KEY', 'test')
    get_settings.cache_clear()
    app = create_app()
    conditions = TripRequirements(destination='도쿄', origin='인천', min_traveler_rating=4.5, desired_satisfaction='재충전')
    analyze = Mock(return_value=(conditions, True, 4, 2))
    monkeypatch.setattr(app.state.analyzer, 'analyze', analyze)
    with TestClient(app) as client:
        headers = {'X-Internal-API-Key': 'test'}
        saved = client.post('/api/v1/trips/prompts', headers=headers, json={
            'prompt': '도쿄', 'origin_hint': '인천', 'accommodation_prompt': '후기 4.5 이상', 'purpose_prompt': '재충전'})
        prompt_id = saved.json()['prompt_id']
        path = f'/api/v1/trips/prompts/{prompt_id}/export'
        assert client.post(path).status_code == 401
        response = client.post(path, headers=headers)
        assert response.status_code == 200
        assert response.headers['content-type'] == 'application/json'
        assert response.headers['content-disposition'] == f'attachment; filename="trip-{prompt_id}.json"'
        assert response.json() == {'destination': '도쿄', 'origin': '인천', 'min_traveler_rating': 4.5,
                                   'desired_satisfaction': '재충전'}
        analyze.assert_called_once()
        prompts = analyze.call_args.args[0]
        assert (prompts.prompt, prompts.accommodation_prompt, prompts.purpose_prompt) == ('도쿄', '후기 4.5 이상', '재충전')
        direct = client.post('/api/v1/trips/export', headers=headers, json=prompts.model_dump(exclude_none=True))
        assert direct.status_code == 200 and direct.json() == response.json()
        new_id = direct.headers['X-Prompt-ID']
        assert new_id != prompt_id
        assert 'Content-Disposition' not in direct.headers
        assert direct.headers['content-type'] == 'application/json'
        assert client.get(f'/api/v1/trips/prompts/{new_id}', headers=headers).json()['prompts']['prompt'] == '도쿄'
        monkeypatch.setattr(app.state.analyzer, 'analyze', Mock(side_effect=RuntimeError()))
        failure = client.post(path, headers=headers)
        assert failure.status_code == 503 and failure.json()['detail']['prompt_id'] == prompt_id
    get_settings.cache_clear()
