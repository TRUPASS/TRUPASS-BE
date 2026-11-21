from abc import ABC, abstractmethod
from app.domain.models import TripRequirements, TransportOption, HotelOption


class TravelProvider(ABC):
    @abstractmethod
    def search_transport(self, requirements: TripRequirements) -> list[TransportOption]: ...

    @abstractmethod
    def search_hotels(self, requirements: TripRequirements, transport: TransportOption) -> list[HotelOption]: ...
