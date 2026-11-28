"""여러 LLM(공급자·모델·키)을 순서대로 시도하는 래퍼.

앞의 항목이 실패하면 다음 항목으로 넘어간다. 모두 실패하면 마지막 오류를 그대로 올려서
라우트의 기존 상태 코드 매핑(502·503)이 바뀌지 않게 한다.
로그에는 `provider:model#번호` 이름만 남기고 키 값은 남기지 않는다.
"""

import logging
import time
from collections.abc import Callable
from typing import Any

import httpx
from google.genai.errors import APIError

from app.domain.models import TripInput, TripRequirements
from app.domain.recommend import Recommendation, RecommendRequest
from app.services.claude import ModelOutputError
from app.services.recommender import InvalidRecommendation

logger = logging.getLogger(__name__)

# 다음 항목으로 넘어가도 되는 오류. 그 밖의 오류(코드 버그 등)는 바로 올린다.
FALLBACK_ERRORS: tuple[type[BaseException], ...] = (
    APIError, httpx.RequestError, RuntimeError, ValueError, InvalidRecommendation,
)


def _is_unavailable(exc: BaseException) -> bool:
    """키·모델 자체가 안 되는 오류인지. 이런 항목은 잠시 건너뛴다.

    응답 내용이 틀린 경우(검증 실패, 거절, 토큰 부족)는 그 입력만의 문제라 건너뛰지 않는다.
    """
    if isinstance(exc, (InvalidRecommendation, ValueError, ModelOutputError)):
        return False
    return isinstance(exc, (APIError, httpx.RequestError, RuntimeError))


class _FallbackChain:
    def __init__(self, entries: list[tuple[str, Any]], cooldown_seconds: float,
                 clock: Callable[[], float] = time.monotonic) -> None:
        if not entries:
            raise ValueError('at least one LLM entry is required')
        self._entries = entries
        self._cooldown = cooldown_seconds
        self._clock = clock
        self._blocked_until: dict[str, float] = {}

    @property
    def labels(self) -> list[str]:
        return [label for label, _ in self._entries]

    def _order(self) -> list[tuple[str, Any]]:
        now = self._clock()
        ready = [entry for entry in self._entries if self._blocked_until.get(entry[0], 0) <= now]
        # 모두 쉬는 중이면 그래도 전부 다시 시도한다. 아무것도 안 해 보고 실패하지 않기 위해서다.
        return ready or list(self._entries)

    def _run(self, call: Callable[[Any], Any]) -> Any:
        last: BaseException | None = None
        for label, target in self._order():
            try:
                result = call(target)
            except FALLBACK_ERRORS as exc:
                last = exc
                if self._cooldown and _is_unavailable(exc):
                    self._blocked_until[label] = self._clock() + self._cooldown
                logger.warning('LLM %s failed (%s), trying next', label, type(exc).__name__)
                continue
            self._blocked_until.pop(label, None)
            logger.info('LLM %s served the request', label)
            return result
        assert last is not None
        raise last


class FallbackAnalyzer(_FallbackChain):
    def analyze(self, request: TripInput) -> tuple[TripRequirements, bool, int | None, int | None]:
        return self._run(lambda analyzer: analyzer.analyze(request))


class FallbackRecommender(_FallbackChain):
    def recommend(self, request: RecommendRequest) -> tuple[Recommendation, bool, int | None, int | None]:
        return self._run(lambda recommender: recommender.recommend(request))
