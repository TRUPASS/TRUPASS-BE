"""core가 고른 항공·숙소 후보로 추천 조합을 만드는 요청·응답 모델.

금액 합계와 예산 비교는 core가 한다. AI는 후보 key(F1, S1 …)로 조합을 고르고 이유만 쓴다.
JSON은 core(Java) AgentClientReqDTO / AgentClientResDTO와 맞춘다.
"""
from datetime import date
from typing import Annotated

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, model_validator

Key = Annotated[str, StringConstraints(strip_whitespace=True, pattern=r'^[FS][0-9]{1,2}$')]


class _Strict(BaseModel):
    model_config = ConfigDict(extra='forbid', populate_by_name=True)


class FlightLeg(_Strict):
    """편도 하나. 시각은 공항 현지 시각 YYYY-MM-DDTHH:mm."""
    from_: str = Field(alias='from')
    to: str
    departing_at: str
    arriving_at: str
    duration_minutes: int | None = Field(default=None, ge=0)
    stops: int = Field(default=0, ge=0)
    flight_numbers: list[str] = []


class FlightCandidate(_Strict):
    """price_krw는 전체 인원 왕복 합계."""
    key: Key
    carrier: str | None = None
    price_krw: int = Field(ge=0)
    outbound: FlightLeg
    inbound: FlightLeg | None = None


class StayCandidate(_Strict):
    """price_krw는 숙박 전체 기간 합계. review_score는 0–10."""
    key: Key
    name: str
    stars: int | None = None
    review_score: float | None = None
    review_count: int | None = None
    price_krw: int = Field(ge=0)
    room: str | None = None
    board: str | None = None
    refundable: bool = False
    address: str | None = None


class TripContext(_Strict):
    """core가 확정한 여행 조건."""
    origin: str
    destination: str
    departure_date: date
    return_date: date
    nights: int = Field(ge=1)
    travelers: int = Field(ge=1)
    budget_krw: int | None = Field(default=None, ge=1)
    purpose: str | None = None
    core_travel_goal: str | None = None
    desired_satisfaction: str | None = None
    accommodation_preferences: list[str] | None = None
    departure_period: str | None = None


class RecommendRequest(_Strict):
    trip: TripContext
    flights: list[FlightCandidate] = Field(min_length=1, max_length=20)
    stays: list[StayCandidate] = Field(min_length=1, max_length=20)
    max_options: int = Field(default=3, ge=1, le=5)

    @model_validator(mode='after')
    def unique_keys(self):
        for kind, keys in (('flights', [f.key for f in self.flights]), ('stays', [s.key for s in self.stays])):
            if len(keys) != len(set(keys)):
                raise ValueError(f'{kind} keys must be unique')
        return self


class RecommendedOption(BaseModel):
    flight_key: str
    stay_key: str
    title: str
    reason: str


class Recommendation(BaseModel):
    """Gemini 응답 스키마. 금액은 넣지 않는다."""
    summary: str
    options: list[RecommendedOption]


class RecommendResponse(Recommendation):
    gemini_called: bool
    prompt_tokens: int | None = None
    output_tokens: int | None = None
