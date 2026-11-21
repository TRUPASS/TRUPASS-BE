"""Adapt the local Spring mock catalog to the travel planner."""

import httpx
from pydantic import BaseModel, Field, TypeAdapter, ValidationError

from app.domain.models import HotelOption, TransportKind, TransportOption, TripRequirements
from app.providers.base import TravelProvider


class MerchantOffer(BaseModel):
    offer_id: str = Field(alias='offerId')
    category: str
    title: str
    amount: int = Field(ge=0)
    currency: str


OFFERS = TypeAdapter(list[MerchantOffer])
SOURCE = 'MOCK MERCHANT / NOT BOOKABLE'

# The fixture exposes route and stay length only through its seeded catalog.
DESTINATION_CODES = {
    '오사카': ('KIX', 'OSA'),
    'osaka': ('KIX', 'OSA'),
    '후쿠오카': ('FUK', 'FUK'),
    'fukuoka': ('FUK', 'FUK'),
}
INCHEON_ORIGINS = {'인천', '인천공항', '인천국제공항', 'incheon', 'icn'}
TOKYO = {'도쿄', '동경', 'tokyo'}
FLIGHT_FIXTURES = (
    ('새벽항공', '06:50', 290_000),
    ('모닝윙', '08:40', 330_000),
    ('하늘여행', '10:20', 370_000),
)
HOTEL_FIXTURES = (
    ('스테이픽', '미술관 인근 조용한 호텔', 95_000, ('미술관', '조용', 'museum', 'quiet')),
    ('룸마켓', '역 근처 시티 호텔', 82_000, ('역', '접근', 'station')),
    ('트래블룸', '도심 부티크 호텔', 125_000, ('도심', '부티크', 'boutique')),
)


class MockMerchantProvider(TravelProvider):
    def _offers(self, merchant_id: str, category: str, prefix: str) -> list[MerchantOffer]:
        try:
            response = httpx.get(
                f'http://127.0.0.1:9001/merchants/{merchant_id}/offers',
                timeout=5.0,
                trust_env=False,
            )
            response.raise_for_status()
            offers = OFFERS.validate_json(response.content)
        except (httpx.HTTPError, ValidationError) as exc:
            raise RuntimeError('Local mock merchant is unavailable') from exc
        return [
            offer for offer in offers
            if offer.category == category and offer.currency == 'KRW'
            and offer.offer_id.startswith(prefix)
        ]

    def search_transport(self, req: TripRequirements) -> list[TransportOption]:
        if not (req.origin and req.destination and req.departure_date and req.return_date):
            return []
        destination = req.destination.strip().casefold()
        route = DESTINATION_CODES.get(destination)
        if (
            (route is None and destination not in TOKYO)
            or req.origin.strip().casefold() not in INCHEON_ORIGINS
            or req.additional_cities
            or (req.transport_preferences and TransportKind.FLIGHT not in req.transport_preferences)
        ):
            return []
        code = route[0] if route else 'TYO'
        spring_offers = self._offers('A', 'FLIGHT', f'A-ICN-{code}-') if route else []
        transports = [
            TransportOption(
                id=offer.offer_id, source=SOURCE, kind=TransportKind.FLIGHT,
                origin=req.origin, destination=req.destination,
                departure_date=req.departure_date, return_date=req.return_date,
                total_price_krw=offer.amount * req.travelers, duration_minutes=0, seller='Mock Air',
            )
            for offer in spring_offers
        ]
        times = {
            'afternoon': ('12:30', '14:10', '16:00'),
            'evening': ('18:10', '19:40', '21:00'),
        }.get(req.departure_period, tuple(fixture[1] for fixture in FLIGHT_FIXTURES))
        for index, (seller, _, price) in enumerate(FLIGHT_FIXTURES):
            transports.append(TransportOption(
                id=f'LOCAL-AIR-{code}-{index + 1}', source='LOCAL MOCK / NOT BOOKABLE',
                seller=seller, departure_time=times[index], kind=TransportKind.FLIGHT,
                origin=req.origin, destination=req.destination, departure_date=req.departure_date,
                return_date=req.return_date, total_price_krw=price * req.travelers,
                duration_minutes=150 if code == 'TYO' else 110,
            ))
        return transports

    def search_hotels(self, req: TripRequirements, transport: TransportOption) -> list[HotelOption]:
        route = DESTINATION_CODES.get(transport.destination.strip().casefold())
        nights = (transport.return_date - transport.departure_date).days
        code = route[1] if route else 'TYO'
        spring_offers = self._offers('B', 'STAY', f'B-{code}-HOTEL-') if route and nights == 2 else []
        hotels = [
            HotelOption(
                id=offer.offer_id, source=SOURCE, name=offer.title,
                city=transport.destination, checkin=transport.departure_date,
                checkout=transport.return_date, total_price_krw=offer.amount, seller='Mock Stay',
            )
            for offer in spring_offers
        ]
        preferences = ' '.join(req.accommodation_preferences).casefold()
        ranked = sorted(enumerate(HOTEL_FIXTURES, start=1),
                        key=lambda item: -sum(tag in preferences for tag in item[1][3]))
        rooms = (req.travelers + 1) // 2
        for index, (seller, name, price, _) in ranked:
            hotels.append(HotelOption(
                id=f'LOCAL-STAY-{code}-{index}', source='LOCAL MOCK / NOT BOOKABLE',
                seller=seller, name=name, city=transport.destination, checkin=transport.departure_date,
                checkout=transport.return_date, total_price_krw=price * nights * rooms,
            ))
        return hotels
