"""Claude(Anthropic)로 조건 추출·조합 추천을 하는 구현.

Gemini 구현과 같은 캐시·검증 흐름을 그대로 쓰고, 모델을 부르는 부분(_extract, _generate)만 바꾼다.
anthropic 패키지는 이 공급자를 쓸 때만 필요하므로 import를 함수 안에서 한다.
"""

import functools
import logging
from typing import Any

from pydantic import BaseModel, create_model

from app.core.cache import TTLCache
from app.domain.models import TripRequirements
from app.domain.recommend import Recommendation
from app.services.analyzer import SYSTEM_INSTRUCTION as EXTRACT_INSTRUCTION
from app.services.analyzer import GeminiAnalyzer
from app.services.recommender import SYSTEM_INSTRUCTION as RECOMMEND_INSTRUCTION
from app.services.recommender import GeminiRecommender

# 거절(refusal)되면 서버가 다른 모델로 한 번 더 시도한다. 이 기능이 있는 모델에만 보낸다.
# Haiku 5.5는 서버 쪽 폴백이 없어서 빼야 요청이 거절되지 않는다.
_SERVER_FALLBACK_MODELS = ('claude-opus-5-5', 'claude-sonnet-5-5')
_SERVER_FALLBACK_BETA = 'server-side-fallback-2026-07-01'

logger = logging.getLogger(__name__)


@functools.cache
def _output_model(model: type[BaseModel]) -> type[BaseModel]:
    """구조화 출력으로 보낼 모델. 선택 필드가 있으면 모든 필드를 필수(값이 없으면 null)로 바꾼 사본을 만든다.

    선택 필드가 많으면 Claude API가 'Schema is too complex'(400)로 거절한다. TripRequirements는 14개가 모두 선택이다.
    필수 + null 허용이면 받는 내용은 같고 문법은 단순해진다. 범위 같은 제약은 원래 모델로 다시 검증한다.
    """
    if all(field.is_required() for field in model.model_fields.values()):
        return model
    fields: dict[str, Any] = {name: (field.annotation, ...) for name, field in model.model_fields.items()}
    return create_model(f'{model.__name__}Output', **fields)


def _api_error_detail(exc: Exception) -> str:
    body = getattr(exc, 'body', None)
    error = body.get('error') if isinstance(body, dict) else None
    if isinstance(error, dict):
        return f"{error.get('type')}: {str(error.get('message'))[:200]}"
    return ''


class ModelOutputError(RuntimeError):
    """모델은 응답했지만 쓸 수 없는 경우(거절, 토큰 부족, 구조화 출력 없음).

    키·모델은 살아 있으므로 폴백 체인이 이 항목을 쉬게 하지 않는다.
    RuntimeError라 라우트에서는 기존처럼 503이 된다.
    """


class _ClaudeCaller:
    """Claude 호출과 응답 검사를 한곳에 모은다. 키 값은 오류 메시지에 절대 넣지 않는다."""

    _key: str
    _model: str

    def _init_claude(self, effort: str, timeout: float) -> None:
        self._effort = effort
        self._timeout = timeout
        self._claude: Any = None

    def _get_client(self) -> Any:
        if self._claude is None:
            import anthropic

            # 재시도는 폴백 체인이 한다. SDK가 같은 키로 또 기다리면 다음 키로 넘어가는 게 늦어진다.
            self._claude = anthropic.Anthropic(api_key=self._key, timeout=self._timeout, max_retries=0)
        return self._claude

    def _call(self, system: str, user_text: str, output_type: type, max_tokens: int) -> tuple[Any, int, int]:
        import anthropic

        wire_type = _output_model(output_type)
        params: dict[str, Any] = {
            'model': self._model,
            'max_tokens': max_tokens,
            'system': system,
            'messages': [{'role': 'user', 'content': user_text}],
            'output_format': wire_type,
        }
        if self._effort:
            params['output_config'] = {'effort': self._effort}
        if self._model.startswith(_SERVER_FALLBACK_MODELS):
            params['betas'] = [_SERVER_FALLBACK_BETA]
            params['fallbacks'] = 'default'
        try:
            response = self._get_client().beta.messages.parse(**params)
        except anthropic.APIError as exc:
            # 라우트가 RuntimeError를 503으로 바꾸면서 원인을 버리므로 여기서 남긴다. 키 값은 들어가지 않는다.
            status = getattr(exc, 'status_code', None)
            logger.warning('Claude %s call failed: %s status=%s %s',
                           self._model, type(exc).__name__, status, _api_error_detail(exc))
            raise RuntimeError(f'Claude call failed: {type(exc).__name__} status={status}') from exc

        if response.stop_reason == 'refusal':
            raise ModelOutputError('Claude refused the request')
        if response.stop_reason == 'max_tokens':
            raise ModelOutputError('Claude response hit max_tokens')
        parsed = response.parsed_output
        if parsed is None:
            raise ModelOutputError('Claude returned no structured output')
        if wire_type is not output_type:
            # 범위를 벗어난 값이면 ValidationError(ValueError)가 나고, 폴백 체인은 다음 항목으로 넘어간다.
            parsed = output_type.model_validate(parsed.model_dump())
        usage = response.usage
        return parsed, int(usage.input_tokens or 0), int(usage.output_tokens or 0)


class ClaudeAnalyzer(_ClaudeCaller, GeminiAnalyzer):
    def __init__(self, api_key: str, model: str, cache: TTLCache, effort: str = 'low', timeout: float = 25) -> None:
        super().__init__(api_key, model, cache)
        self._init_claude(effort, timeout)

    def _extract(self, user_text: str) -> tuple[TripRequirements, int, int]:
        # 생각(thinking) 토큰도 max_tokens에 들어가므로 출력 크기보다 넉넉하게 잡는다.
        return self._call(EXTRACT_INSTRUCTION, user_text, TripRequirements, 8192)


class ClaudeRecommender(_ClaudeCaller, GeminiRecommender):
    def __init__(self, api_key: str, model: str, cache: TTLCache, effort: str = 'low', timeout: float = 25) -> None:
        super().__init__(api_key, model, cache)
        self._init_claude(effort, timeout)

    def _generate(self, user_text: str) -> tuple[Recommendation, int, int]:
        return self._call(RECOMMEND_INSTRUCTION, user_text, Recommendation, 16000)
