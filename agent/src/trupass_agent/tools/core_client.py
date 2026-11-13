"""core 내부 도구 API(`/internal/tools/*`) 클라이언트.

에이전트는 Duffel·TourAPI 같은 외부 API를 직접 부르지 않는다. 필요한 데이터는 모두 core를 거친다.
"""

from datetime import date
from types import TracebackType
from typing import Self

import httpx
from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

from trupass_agent.config import Settings
from trupass_agent.schemas import OfferCategory, OfferRef


class SearchOffersRequest(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    destination: str
    start_date: date
    end_date: date
    travelers: int = Field(ge=1)
    categories: list[OfferCategory] | None = None


class SearchOffersResponse(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    offers: list[OfferRef]


class CoreApiResponse[T](BaseModel):
    """core 공통 응답 형식(`ApiResponse`): `{"isSuccess", "code", "message", "result"}`."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    is_success: bool
    code: str
    message: str
    result: T | None = None


class CoreApiError(Exception):
    """core가 `isSuccess=false`로 응답했다. `code`는 core의 에러 코드(예: `MERCHANT409_1`)다."""

    def __init__(self, status_code: int, code: str, message: str):
        super().__init__(f"{status_code} {code}: {message}")
        self.status_code = status_code
        self.code = code
        self.message = message


class CoreClient:
    """core 내부 도구 API를 부르는 비동기 클라이언트. `async with`로 쓴다."""

    def __init__(self, base_url: str, timeout_seconds: float = 10.0, transport: httpx.AsyncBaseTransport | None = None):
        self._client = httpx.AsyncClient(base_url=base_url, timeout=timeout_seconds, transport=transport)

    @classmethod
    def from_settings(cls, settings: Settings) -> Self:
        return cls(settings.core_base_url, settings.core_timeout_seconds)

    async def search_offers(self, request: SearchOffersRequest) -> SearchOffersResponse:
        """`POST /internal/tools/search-offers`. core 쪽은 MerchantClient 구현 전까지 501(COMMON501)을 돌려준다."""
        response = await self._client.post(
            "/internal/tools/search-offers",
            json=request.model_dump(mode="json", by_alias=True, exclude_none=True),
        )
        return _unwrap(response, SearchOffersResponse)

    async def aclose(self) -> None:
        await self._client.aclose()

    async def __aenter__(self) -> Self:
        return self

    async def __aexit__(
        self,
        exc_type: type[BaseException] | None,
        exc: BaseException | None,
        tb: TracebackType | None,
    ) -> None:
        await self.aclose()


def _unwrap[T: BaseModel](response: httpx.Response, result_type: type[T]) -> T:
    """core 공통 응답에서 `result`를 꺼낸다. 실패 응답이면 `CoreApiError`를 던진다."""
    try:
        body = CoreApiResponse[result_type].model_validate(response.json())  # type: ignore[valid-type]
    except ValueError:
        response.raise_for_status()
        raise
    if not body.is_success or body.result is None:
        raise CoreApiError(response.status_code, body.code, body.message)
    return body.result
