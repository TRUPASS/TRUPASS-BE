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


class CoreClient:
    """core 내부 도구 API를 부르는 비동기 클라이언트. `async with`로 쓴다."""

    def __init__(self, base_url: str, timeout_seconds: float = 10.0, transport: httpx.AsyncBaseTransport | None = None):
        self._client = httpx.AsyncClient(base_url=base_url, timeout=timeout_seconds, transport=transport)

    @classmethod
    def from_settings(cls, settings: Settings) -> Self:
        return cls(settings.core_base_url, settings.core_timeout_seconds)

    async def search_offers(self, request: SearchOffersRequest) -> SearchOffersResponse:
        """`POST /internal/tools/search-offers`. core 쪽은 아직 501을 돌려준다."""
        response = await self._client.post(
            "/internal/tools/search-offers",
            json=request.model_dump(mode="json", by_alias=True, exclude_none=True),
        )
        response.raise_for_status()
        return SearchOffersResponse.model_validate(response.json())

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
