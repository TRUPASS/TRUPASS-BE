"""에이전트가 내놓는 패키지 출력 스키마(초안).

원칙: 금액은 반드시 오퍼 ID와 함께만 다룬다. 에이전트(LLM)는 금액을 만들거나 고치지 않고,
core가 돌려준 오퍼의 금액을 `OfferRef`에 그대로 옮긴다. 옵션 합계는 계산 속성으로만 노출한다.

JSON은 core·FE와 맞추기 위해 camelCase로 직렬화한다(`model_dump(by_alias=True)`).
"""

from datetime import datetime
from enum import StrEnum

from pydantic import BaseModel, ConfigDict, Field, computed_field, model_validator
from pydantic.alias_generators import to_camel


class _CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, frozen=True)


class OfferCategory(StrEnum):
    FLIGHT = "FLIGHT"
    STAY = "STAY"
    EXPERIENCE = "EXPERIENCE"


class OfferRef(_CamelModel):
    """core가 돌려준 오퍼 1건에 대한 참조. 금액은 원 단위 정수다."""

    offer_id: str = Field(min_length=1)
    merchant_id: str = Field(min_length=1)
    category: OfferCategory
    title: str
    amount: int = Field(ge=0)
    currency: str = Field(pattern=r"^[A-Z]{3}$")
    expires_at: datetime | None = None


class PackageOption(_CamelModel):
    """사용자에게 보여줄 패키지 1안."""

    option_id: str = Field(min_length=1)
    title: str
    summary: str
    offers: list[OfferRef] = Field(min_length=1)

    @model_validator(mode="after")
    def _single_currency(self) -> "PackageOption":
        if len({offer.currency for offer in self.offers}) > 1:
            raise ValueError("한 패키지 안의 오퍼는 같은 통화여야 한다")
        return self

    @computed_field  # type: ignore[prop-decorator]
    @property
    def total_amount(self) -> int:
        return sum(offer.amount for offer in self.offers)

    @computed_field  # type: ignore[prop-decorator]
    @property
    def currency(self) -> str:
        return self.offers[0].currency


class TravelPackage(_CamelModel):
    """자연어 요청 1건에 대한 에이전트 응답. 패키지 2~3안."""

    request_summary: str
    options: list[PackageOption] = Field(min_length=2, max_length=3)
