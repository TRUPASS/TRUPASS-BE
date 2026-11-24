from datetime import date
from typing import Annotated, Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, model_validator

Text = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]


class TripInput(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True, extra='forbid')
    prompt: str | None = Field(default=None, max_length=3000)
    accommodation_prompt: str | None = Field(default=None, max_length=1000)
    purpose_prompt: str | None = Field(default=None, max_length=1000)
    origin_hint: str | None = Field(default=None, max_length=100)
    currency: Literal['KRW'] = 'KRW'


class PromptUpdate(TripInput):
    model_config = ConfigDict(json_schema_extra={'examples': [{
        'prompt': '도쿄에 가고 싶어',
        'accommodation_prompt': '실제 여행자 후기 4.5점 이상인 숙소',
        'purpose_prompt': '가족과 대화하는 게 최우선이야. 함께 재충전하고 싶어.',
    }]})
    prompt_id: UUID | None = None


class SavedPrompts(BaseModel):
    prompt_id: UUID
    prompts: TripInput


class TripRequirements(BaseModel):
    origin: Text | None = None
    destination: Text | None = None
    departure_date: date | None = None
    return_date: date | None = None
    travelers: int | None = Field(default=None, ge=1, le=20)
    budget_krw: int | None = Field(default=None, ge=1)
    purpose: Text | None = None
    core_travel_goal: Text | None = None
    desired_satisfaction: Text | None = None
    min_traveler_rating: float | None = Field(default=None, ge=1, le=5, strict=True)
    accommodation_preferences: list[Text] | None = None
    transport_preferences: list[Literal['flight', 'train', 'bus', 'ferry']] | None = None
    departure_period: Literal['morning', 'afternoon', 'evening'] | None = None
    additional_cities: list[Text] | None = None

    @model_validator(mode='before')
    @classmethod
    def omit_empty_values(cls, values):
        if isinstance(values, dict):
            return {k: v for k, v in values.items() if v != [] and not (isinstance(v, str) and not v.strip())}
        return values

    @model_validator(mode='after')
    def valid_date_range(self):
        if self.departure_date and self.return_date and self.return_date <= self.departure_date:
            raise ValueError('return_date must be later than departure_date')
        return self


class PlanResponse(BaseModel):
    prompt_id: UUID
    requirements: TripRequirements
    gemini_called: bool
    prompt_tokens: int | None = None
    output_tokens: int | None = None


class MoneyBreakdown(BaseModel):
    transport_krw: int
    hotel_krw: int
    estimated_local_transport_krw: int
    estimated_other_krw: int
    total_krw: int
    over_budget_krw: int
    remaining_budget_krw: int
    is_over_budget: bool


class RepriceRequest(BaseModel):
    budget_krw: int = Field(ge=1)
    transport_krw: int = Field(ge=0)
    hotel_krw: int = Field(ge=0)
    estimated_local_transport_krw: int = Field(ge=0)
    estimated_other_krw: int = Field(ge=0)
