from copy import deepcopy
from unittest.mock import Mock

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.core.cache import TTLCache
from app.core.config import get_settings
from app.domain.recommend import Recommendation, RecommendedOption, RecommendRequest
from app.main import create_app
from app.services.recommender import SYSTEM_INSTRUCTION, GeminiRecommender, InvalidRecommendation

# core(Java) AgentClientReqDTO.Recommend가 보내는 모양 그대로.
PAYLOAD = {
    'trip': {'origin': '서울(SEL)', 'destination': '오사카(OSA)', 'departure_date': '2026-12-20',
             'return_date': '2026-12-22', 'nights': 2, 'travelers': 2, 'budget_krw': 1200000,
             'accommodation_preferences': ['조식'], 'departure_period': 'morning'},
    'flights': [
        {'key': 'F1', 'carrier': 'Asiana', 'price_krw': 420000,
         'outbound': {'from': 'ICN', 'to': 'KIX', 'departing_at': '2026-12-20T08:30', 'arriving_at': '2026-12-20T10:15',
                      'duration_minutes': 105, 'stops': 0, 'flight_numbers': ['OZ112']},
         'inbound': {'from': 'KIX', 'to': 'ICN', 'departing_at': '2026-12-22T18:00', 'arriving_at': '2026-12-22T19:55',
                     'duration_minutes': 115, 'stops': 0, 'flight_numbers': ['OZ113']}},
        {'key': 'F2', 'carrier': 'Jeju Air', 'price_krw': 310000,
         'outbound': {'from': 'ICN', 'to': 'KIX', 'departing_at': '2026-12-20T14:00', 'arriving_at': '2026-12-20T15:50',
                      'duration_minutes': 110, 'stops': 0, 'flight_numbers': ['7C1302']}},
    ],
    'stays': [
        {'key': 'S1', 'name': 'Hotel A', 'stars': 3, 'review_score': 8.4, 'review_count': 812, 'price_krw': 260000,
         'room': 'Double', 'board': 'Room Only', 'refundable': False, 'address': 'Namba'},
        {'key': 'S2', 'name': 'Hotel B', 'stars': 4, 'review_score': 9.1, 'review_count': 420, 'price_krw': 540000,
         'room': 'Twin', 'board': 'Breakfast Included', 'refundable': True},
    ],
    'max_options': 3,
}


def option(flight: str, stay: str, title: str = '균형형') -> RecommendedOption:
    return RecommendedOption(flight_key=flight, stay_key=stay, title=title, reason='직항 오전 출발, 조식 포함.')


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setenv('INTERNAL_API_KEY', 'test')
    get_settings.cache_clear()
    app = create_app()
    with TestClient(app, headers={'X-Internal-API-Key': 'test'}) as test_client:
        yield app, test_client
    get_settings.cache_clear()


def test_requires_internal_key(client):
    app, test_client = client
    assert test_client.post('/api/v1/trips/recommend', json=PAYLOAD,
                            headers={'X-Internal-API-Key': 'wrong'}).status_code == 401


def test_recommend_returns_options_without_amounts(client, monkeypatch):
    app, test_client = client
    recommendation = Recommendation(summary='예산 안 조합 두 개입니다.', options=[option('F1', 'S2'), option('F2', 'S1', '최저가')])
    recommend = Mock(return_value=(recommendation, True, 900, 150))
    monkeypatch.setattr(app.state.recommender, 'recommend', recommend)

    response = test_client.post('/api/v1/trips/recommend', json=PAYLOAD)

    assert response.status_code == 200
    body = response.json()
    assert [(o['flight_key'], o['stay_key']) for o in body['options']] == [('F1', 'S2'), ('F2', 'S1')]
    assert body['gemini_called'] is True and body['prompt_tokens'] == 900
    sent: RecommendRequest = recommend.call_args.args[0]
    assert sent.flights[0].outbound.from_ == 'ICN'
    assert sent.flights[1].inbound is None


def test_rejects_unknown_fields_and_duplicate_keys(client):
    _, test_client = client
    extra = deepcopy(PAYLOAD)
    extra['trip']['currency'] = 'KRW'
    assert test_client.post('/api/v1/trips/recommend', json=extra).status_code == 422

    duplicated = deepcopy(PAYLOAD)
    duplicated['stays'][1]['key'] = 'S1'
    assert test_client.post('/api/v1/trips/recommend', json=duplicated).status_code == 422


def test_unknown_keys_only_is_502(client, monkeypatch):
    app, test_client = client
    monkeypatch.setattr(app.state.recommender, 'recommend', Mock(side_effect=InvalidRecommendation('x')))
    assert test_client.post('/api/v1/trips/recommend', json=PAYLOAD).status_code == 502


def test_gemini_failure_is_503(client, monkeypatch):
    app, test_client = client
    monkeypatch.setattr(app.state.recommender, 'recommend', Mock(side_effect=RuntimeError('down')))
    assert test_client.post('/api/v1/trips/recommend', json=PAYLOAD).status_code == 503


def test_keep_valid_drops_unknown_and_duplicate_pairs_and_caps():
    request = RecommendRequest.model_validate({**PAYLOAD, 'max_options': 2})
    raw = Recommendation(summary='s', options=[
        option('F9', 'S1'), option(' F1 ', 'S1'), option('F1', 'S1'), option('F2', 'S3'), option('F2', 'S2'),
        option('F1', 'S2')])

    kept = GeminiRecommender.keep_valid(raw, request)

    assert [(o.flight_key, o.stay_key) for o in kept.options] == [('F1', 'S1'), ('F2', 'S2')]


def test_recommend_calls_gemini_once_and_caches():
    recommender = GeminiRecommender('fake-key', 'model', TTLCache(600, 8))
    generate = Mock(return_value=(Recommendation(summary='s', options=[option('F1', 'S1')]), 10, 5))
    recommender._generate = generate
    request = RecommendRequest.model_validate(PAYLOAD)

    first = recommender.recommend(request)
    second = recommender.recommend(request)

    assert first[1] is True and second[1] is False
    assert generate.call_count == 1
    assert '"from":"ICN"' in generate.call_args.args[0]


def test_all_invalid_raises():
    recommender = GeminiRecommender('fake-key', 'model', TTLCache(600, 8))
    recommender._generate = Mock(return_value=(Recommendation(summary='s', options=[option('F7', 'S7')]), 1, 1))
    with pytest.raises(InvalidRecommendation):
        recommender.recommend(RecommendRequest.model_validate(PAYLOAD))


def test_missing_key_raises_runtime_error():
    with pytest.raises(RuntimeError):
        GeminiRecommender('', 'model', TTLCache(600, 8)).recommend(RecommendRequest.model_validate(PAYLOAD))


def test_request_needs_candidates():
    with pytest.raises(ValidationError):
        RecommendRequest.model_validate({**PAYLOAD, 'stays': []})


def test_prompt_documents_output_fields_and_forbids_amounts():
    for field in ('summary', 'options', 'options[].flight_key', 'options[].stay_key', 'options[].title',
                  'options[].reason'):
        assert f'| {field} |' in SYSTEM_INSTRUCTION
    assert '금액 숫자' in SYSTEM_INSTRUCTION
    assert 'max_options' in SYSTEM_INSTRUCTION
