"""Offline fake provider. Never present mock amounts as bookable live prices."""
from app.domain.models import TripRequirements, TransportOption, HotelOption, TransportKind
from app.providers.base import TravelProvider


class MockTravelProvider(TravelProvider):
    def search_transport(self, req: TripRequirements) -> list[TransportOption]:
        if not (req.origin and req.destination and req.departure_date and req.return_date):
            return []
        # Toy routing only: not a real geographical classifier. Production needs route APIs.
        kinds = req.transport_preferences or [TransportKind.TRAIN, TransportKind.BUS, TransportKind.FLIGHT]
        prices = {TransportKind.TRAIN: 210_000, TransportKind.BUS: 95_000,
                  TransportKind.FLIGHT: 340_000, TransportKind.FERRY: 180_000}
        duration = {TransportKind.TRAIN: 180, TransportKind.BUS: 300,
                    TransportKind.FLIGHT: 100, TransportKind.FERRY: 400}
        return [TransportOption(id=f'mock-{kind.value}', source='MOCK / NOT BOOKABLE',
            kind=kind, origin=req.origin, destination=req.destination,
            departure_date=req.departure_date, return_date=req.return_date,
            total_price_krw=prices[kind] * req.travelers,
            duration_minutes=duration[kind]) for kind in kinds]

    def search_hotels(self, req: TripRequirements, transport: TransportOption) -> list[HotelOption]:
        nights = (transport.return_date - transport.departure_date).days
        if nights <= 0:
            return []
        return [HotelOption(id=f'mock-h{i}', source='MOCK / NOT BOOKABLE',
            name=f'가상 숙소 {i}', city=transport.destination,
            checkin=transport.departure_date, checkout=transport.return_date,
            total_price_krw=nights * per_night * max(1, (req.travelers + 1) // 2))
            for i, per_night in enumerate([65_000, 95_000, 135_000], start=1)]
