from datetime import date
from enum import Enum
from typing import Literal
from pydantic import BaseModel, Field, model_validator


class TransportKind(str, Enum):
    FLIGHT = 'flight'
    TRAIN = 'train'
    BUS = 'bus'
    FERRY = 'ferry'


class TripInput(BaseModel):
    # Existing preview separates 3 free-text inputs; a single prompt is also possible.
    prompt: str = Field(min_length=5, max_length=3000)
    accommodation_prompt: str = Field(default='', max_length=1000)
    purpose_prompt: str = Field(default='', max_length=1000)
    origin_hint: str | None = Field(default=None, max_length=100)
    currency: Literal['KRW'] = 'KRW'


class TripRequirements(BaseModel):
    origin: str | None = None
    destination: str | None = None
    departure_date: date | None = None
    return_date: date | None = None
    travelers: int = Field(default=1, ge=1, le=20)
    budget_krw: int | None = Field(default=None, ge=1)
    purpose: str = '관광'
    accommodation_preferences: list[str] = Field(default_factory=list)
    transport_preferences: list[TransportKind] = Field(default_factory=list)
    departure_period: Literal['morning', 'afternoon', 'evening'] | None = None
    additional_cities: list[str] = Field(default_factory=list)

    @model_validator(mode='after')
    def valid_date_range(self):
        if self.departure_date and self.return_date and self.return_date <= self.departure_date:
            raise ValueError('return_date must be later than departure_date')
        return self


class MoneyBreakdown(BaseModel):
    transport_krw: int
    hotel_krw: int
    estimated_local_transport_krw: int
    estimated_other_krw: int
    total_krw: int
    over_budget_krw: int
    remaining_budget_krw: int
    is_over_budget: bool


class TransportOption(BaseModel):
    id: str
    source: str
    kind: TransportKind
    origin: str
    destination: str
    departure_date: date
    return_date: date
    total_price_krw: int = Field(ge=0, description='All travelers, round trip if applicable')
    duration_minutes: int = Field(ge=0)
    seller: str | None = None
    departure_time: str | None = None
    is_live: bool = False
    booking_url: str | None = None


class HotelOption(BaseModel):
    id: str
    source: str
    name: str
    city: str
    checkin: date
    checkout: date
    total_price_krw: int = Field(ge=0, description='Total stay cost for all travelers')
    seller: str | None = None
    is_live: bool = False
    booking_url: str | None = None


class TripCombination(BaseModel):
    id: str
    transport: TransportOption
    hotel: HotelOption
    budget: MoneyBreakdown
    strategy: Literal['budget', 'balanced', 'comfort']
    notes: list[str] = Field(default_factory=list)


class ItineraryDay(BaseModel):
    day: int
    area: str
    stops: list[str]


class PlanResponse(BaseModel):
    requirements: TripRequirements
    missing_fields: list[str]
    combinations: list[TripCombination]
    quote_status: Literal['mock', 'live']
    message: str
    gemini_called: bool
    prompt_tokens: int | None = None
    output_tokens: int | None = None
    transport_options: list[TransportOption] = Field(default_factory=list)
    hotel_options: list[HotelOption] = Field(default_factory=list)
    itinerary: list[ItineraryDay] = Field(default_factory=list)
    assumed_return_date: date | None = None


class RepriceRequest(BaseModel):
    budget_krw: int = Field(ge=1)
    transport_krw: int = Field(ge=0)
    hotel_krw: int = Field(ge=0)
    estimated_local_transport_krw: int = Field(ge=0)
    estimated_other_krw: int = Field(ge=0)
