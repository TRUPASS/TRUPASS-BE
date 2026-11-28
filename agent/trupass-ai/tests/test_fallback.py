from types import SimpleNamespace
from unittest.mock import Mock

import anthropic
import httpx
import pytest
from fastapi import FastAPI

from app.bootstrap import install_planner
from app.core.cache import TTLCache
from app.core.config import get_settings
from app.domain.models import TripInput, TripRequirements
from app.domain.recommend import Recommendation, RecommendedOption, RecommendRequest
from app.services.analyzer import GeminiAnalyzer
from app.services.claude import ClaudeAnalyzer, ClaudeRecommender, ModelOutputError
from app.services.fallback import FallbackAnalyzer, FallbackRecommender
from app.services.recommender import GeminiRecommender, InvalidRecommendation
from tests.test_recommend import PAYLOAD

LLM_ENV = ('LLM_PROVIDERS', 'ANTHROPIC_API_KEYS', 'ANTHROPIC_API_KEY', 'ANTHROPIC_MODELS', 'GEMINI_API_KEYS',
           'GEMINI_API_KEY', 'GOOGLE_API_KEY', 'GEMINI_MODELS', 'LLM_COOLDOWN_SECONDS')
RESULT = (TripRequirements(destination='오사카'), True, 10, 5)


@pytest.fixture
def env(monkeypatch, tmp_path):
    # 개발자 PC의 실제 키나 .env가 테스트에 섞이지 않게 한다.
    monkeypatch.chdir(tmp_path)
    for name in LLM_ENV:
        monkeypatch.delenv(name, raising=False)
    get_settings.cache_clear()
    yield monkeypatch
    get_settings.cache_clear()


def entry(label, side_effect):
    return label, SimpleNamespace(analyze=Mock(side_effect=side_effect), recommend=Mock(side_effect=side_effect))


class Clock:
    def __init__(self):
        self.now = 1000.0

    def __call__(self):
        return self.now


def test_falls_back_to_next_entry_on_runtime_error():
    first = entry('anthropic:a#1', RuntimeError('credit'))
    second = entry('anthropic:a#2', [RESULT])
    chain = FallbackAnalyzer([first, second], cooldown_seconds=60)
    assert chain.analyze(TripInput(prompt='오사카')) == RESULT
    assert first[1].analyze.call_count == 1


def test_invalid_output_falls_back_without_cooldown():
    clock = Clock()
    first = entry('anthropic:a#1', InvalidRecommendation('none'))
    second = entry('gemini:g#1', lambda request: 'ok')
    chain = FallbackRecommender([first, second], cooldown_seconds=60, clock=clock)
    assert chain.recommend(Mock()) == 'ok'
    assert chain.recommend(Mock()) == 'ok'
    # 응답 내용 문제는 그 입력만의 문제라 다음 요청에서도 첫 항목부터 다시 시도한다.
    assert first[1].recommend.call_count == 2


def test_unavailable_entry_is_skipped_until_cooldown_ends():
    clock = Clock()
    first = entry('anthropic:a#1', httpx.ConnectError('down'))
    second = entry('gemini:g#1', lambda request: 'ok')
    chain = FallbackAnalyzer([first, second], cooldown_seconds=60, clock=clock)
    chain.analyze(Mock())
    chain.analyze(Mock())
    assert first[1].analyze.call_count == 1
    clock.now += 61
    chain.analyze(Mock())
    assert first[1].analyze.call_count == 2


def test_refusal_does_not_cool_down():
    clock = Clock()
    first = entry('anthropic:a#1', ModelOutputError('refused'))
    second = entry('gemini:g#1', lambda request: 'ok')
    chain = FallbackAnalyzer([first, second], cooldown_seconds=60, clock=clock)
    chain.analyze(Mock())
    chain.analyze(Mock())
    assert first[1].analyze.call_count == 2


def test_all_cooling_down_still_tries_everything():
    clock = Clock()
    only = entry('anthropic:a#1', [RuntimeError('down'), RuntimeError('down'), RESULT])
    other = entry('anthropic:a#2', [RuntimeError('down'), RuntimeError('down')])
    chain = FallbackAnalyzer([only, other], cooldown_seconds=60, clock=clock)
    with pytest.raises(RuntimeError):
        chain.analyze(Mock())
    with pytest.raises(RuntimeError):
        chain.analyze(Mock())
    assert chain.analyze(Mock()) == RESULT


def test_last_error_is_raised_when_everything_fails():
    chain = FallbackRecommender([entry('a', RuntimeError('first')), entry('b', InvalidRecommendation('last'))],
                                cooldown_seconds=0)
    with pytest.raises(InvalidRecommendation, match='last'):
        chain.recommend(Mock())


def test_unexpected_errors_are_not_swallowed():
    second = entry('b', [RESULT])
    chain = FallbackAnalyzer([entry('a', KeyError('bug')), second], cooldown_seconds=0)
    with pytest.raises(KeyError):
        chain.analyze(Mock())
    second[1].analyze.assert_not_called()


def test_bootstrap_builds_claude_first_then_gemini(env):
    env.setenv('LLM_PROVIDERS', 'anthropic,gemini')
    env.setenv('ANTHROPIC_API_KEYS', 'key-a, key-b')
    env.setenv('ANTHROPIC_MODELS', 'claude-opus-5-5,claude-haiku-5-5')
    env.setenv('GEMINI_API_KEY', 'gem-key')
    app = FastAPI()
    install_planner(app)
    assert isinstance(app.state.analyzer, FallbackAnalyzer)
    assert isinstance(app.state.recommender, FallbackRecommender)
    assert app.state.analyzer.labels == [
        'anthropic:claude-opus-5-5#1', 'anthropic:claude-opus-5-5#2',
        'anthropic:claude-haiku-5-5#1', 'anthropic:claude-haiku-5-5#2',
        'gemini:gemini-2.5-flash-lite#1',
    ]
    # 이름에는 키 번호만 들어간다.
    assert not any('key-' in label or 'gem-key' in label for label in app.state.analyzer.labels)
    kinds = [type(target) for _, target in app.state.recommender._entries]
    assert kinds == [ClaudeRecommender] * 4 + [GeminiRecommender]


def test_bootstrap_default_is_single_gemini_without_wrapper(env):
    env.setenv('GEMINI_API_KEY', 'gem-key')
    app = FastAPI()
    install_planner(app)
    assert type(app.state.analyzer) is GeminiAnalyzer
    assert type(app.state.recommender) is GeminiRecommender


def test_bootstrap_without_any_key_keeps_old_behavior(env):
    env.setenv('LLM_PROVIDERS', 'anthropic')
    app = FastAPI()
    install_planner(app)
    assert type(app.state.analyzer) is GeminiAnalyzer
    with pytest.raises(RuntimeError, match='not configured'):
        app.state.analyzer.analyze(TripInput(prompt='오사카'))


def test_bootstrap_rejects_unknown_provider(env):
    env.setenv('LLM_PROVIDERS', 'anthropic,openai')
    env.setenv('ANTHROPIC_API_KEYS', 'key-a')
    with pytest.raises(ValueError, match='openai'):
        install_planner(FastAPI())


def claude_response(parsed, stop_reason='end_turn'):
    return SimpleNamespace(stop_reason=stop_reason, parsed_output=parsed,
                           usage=SimpleNamespace(input_tokens=120, output_tokens=40))


def fake_client(*responses):
    parse = Mock(side_effect=list(responses))
    return SimpleNamespace(beta=SimpleNamespace(messages=SimpleNamespace(parse=parse))), parse


def test_claude_analyzer_sends_structured_request_with_server_fallback():
    analyzer = ClaudeAnalyzer('fake-key', 'claude-opus-5-5', TTLCache(600, 8), effort='low')
    analyzer._claude, parse = fake_client(claude_response(TripRequirements(destination='오사카')))
    requirements, called, inputs, outputs = analyzer.analyze(TripInput(prompt='오사카 2박', origin_hint='서울'))
    assert (requirements.destination, requirements.origin, called, inputs, outputs) == ('오사카', '서울', True, 120, 40)
    kwargs = parse.call_args.kwargs
    assert kwargs['model'] == 'claude-opus-5-5'
    # 선택 필드가 많으면 API가 'Schema is too complex'로 거절해서, 모든 필드를 필수(null 허용)로 바꾼 사본을 보낸다.
    sent = kwargs['output_format'].model_json_schema()
    assert set(sent['required']) == set(TripRequirements.model_fields)
    assert kwargs['output_config'] == {'effort': 'low'}
    assert kwargs['fallbacks'] == 'default'
    assert kwargs['betas'] == ['server-side-fallback-2026-07-01']
    assert 'temperature' not in kwargs
    # 같은 입력은 캐시에서 돌려준다.
    assert analyzer.analyze(TripInput(prompt='오사카 2박', origin_hint='서울'))[1] is False
    assert parse.call_count == 1


def test_claude_output_is_validated_against_original_model():
    analyzer = ClaudeAnalyzer('fake-key', 'claude-opus-5-5', TTLCache(600, 8))
    # 보낸 사본에는 범위 제약이 없어서 travelers=0도 올 수 있다. 원래 모델로 다시 검증해서 막는다.
    analyzer._claude, _ = fake_client(claude_response(SimpleNamespace(model_dump=lambda: {'travelers': 0})))
    with pytest.raises(ValueError):
        analyzer.analyze(TripInput(prompt='도쿄'))


def test_haiku_has_no_server_fallback():
    analyzer = ClaudeAnalyzer('fake-key', 'claude-haiku-5-5', TTLCache(600, 8))
    analyzer._claude, parse = fake_client(claude_response(TripRequirements()))
    analyzer.analyze(TripInput(prompt='도쿄'))
    assert 'fallbacks' not in parse.call_args.kwargs
    assert 'betas' not in parse.call_args.kwargs


def test_claude_recommender_runs_the_same_validation():
    recommender = ClaudeRecommender('fake-key', 'claude-opus-5-5', TTLCache(600, 8))
    recommendation = Recommendation(summary='요약', options=[
        RecommendedOption(flight_key='F1', stay_key='S2', title='편한 일정', reason='직항, 조식 포함.'),
        RecommendedOption(flight_key='F9', stay_key='S1', title='없는 항공', reason='-'),
    ])
    recommender._claude, parse = fake_client(claude_response(recommendation))
    result, called, *_ = recommender.recommend(RecommendRequest.model_validate(PAYLOAD))
    assert [(o.flight_key, o.stay_key) for o in result.options] == [('F1', 'S2')]
    assert parse.call_args.kwargs['output_format'] is Recommendation


@pytest.mark.parametrize('stop_reason', ['refusal', 'max_tokens'])
def test_claude_unusable_stop_reason_raises(stop_reason):
    analyzer = ClaudeAnalyzer('fake-key', 'claude-opus-5-5', TTLCache(600, 8))
    analyzer._claude, _ = fake_client(claude_response(None, stop_reason))
    with pytest.raises(ModelOutputError):
        analyzer.analyze(TripInput(prompt='도쿄'))


def test_claude_api_error_becomes_runtime_error_without_key():
    analyzer = ClaudeAnalyzer('secret-key-value', 'claude-opus-5-5', TTLCache(600, 8))
    error = anthropic.APIConnectionError(request=httpx.Request('POST', 'https://api.anthropic.com/v1/messages'))
    analyzer._claude, _ = fake_client(error)
    with pytest.raises(RuntimeError) as caught:
        analyzer.analyze(TripInput(prompt='도쿄'))
    assert not isinstance(caught.value, ModelOutputError)
    assert 'secret-key-value' not in str(caught.value)


def test_claude_client_does_not_retry_by_itself():
    client = ClaudeAnalyzer('fake-key', 'claude-opus-5-5', TTLCache(600, 8), timeout=12)._get_client()
    assert client.max_retries == 0


def test_empty_env_values_use_defaults(env):
    # 루트 .env.example처럼 `KEY=`로 비워 둔 값이 compose로 넘어와도 기본값을 쓴다.
    for name in ('LLM_PROVIDERS', 'ANTHROPIC_MODELS', 'ANTHROPIC_EFFORT', 'GEMINI_MODEL', 'GEMINI_API_KEY',
                 'LLM_TIMEOUT_SECONDS', 'LLM_COOLDOWN_SECONDS'):
        env.setenv(name, '')
    env.setenv('GOOGLE_API_KEY', 'gem-key')
    settings = get_settings()
    assert settings.provider_order() == ['gemini']
    assert settings.models_for('anthropic') == ['claude-opus-5-5']
    assert settings.anthropic_effort == 'low'
    assert settings.models_for('gemini') == ['gemini-2.5-flash-lite']
    assert settings.keys_for('gemini') == ['gem-key']
    assert settings.llm_timeout_seconds == 25
