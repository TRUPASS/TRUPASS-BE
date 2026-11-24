from datetime import datetime, timedelta
from zoneinfo import ZoneInfo
from app.domain.models import TripInput, TripRequirements, PlanResponse
from app.providers.base import TravelProvider
from app.services.analyzer import GeminiAnalyzer
from app.services.combiner import CombinationEngine
from app.services.itinerary import recommend_itinerary


class TripPlanner:
    def __init__(self, analyzer: GeminiAnalyzer, provider: TravelProvider, combiner: CombinationEngine):
        self._analyzer = analyzer
        self._provider = provider
        self._combiner = combiner

    def plan(self, request: TripInput) -> PlanResponse:
        requirements, called, prompt_tokens, output_tokens = self._analyzer.analyze(request)
        missing_fields = [field for field in ('origin', 'destination', 'departure_date')
                          if getattr(requirements, field) is None]
        if (requirements.departure_date
                and requirements.departure_date < datetime.now(ZoneInfo("Asia/Seoul")).date()):
            missing_fields.append('future_departure_date')
        if missing_fields:
            return PlanResponse(requirements=requirements, missing_fields=missing_fields, combinations=[],
                quote_status='mock', message='필수 여행 조건이 부족하거나 날짜가 과거입니다.',
                gemini_called=called, prompt_tokens=prompt_tokens, output_tokens=output_tokens)

        assumed_return = None
        search_requirements = requirements
        if requirements.return_date is None and requirements.departure_date is not None:
            assumed_return = requirements.departure_date + timedelta(days=2)
            search_requirements = requirements.model_copy(update={'return_date': assumed_return})

        transports = self._provider.search_transport(search_requirements)
        # All current transport options share the same destination and travel dates.
        hotels = self._provider.search_hotels(search_requirements, transports[0]) if transports else []
        combinations = self._combiner.combine(
            search_requirements, transports, {transport.id: hotels for transport in transports})
        return PlanResponse(requirements=requirements, missing_fields=[], combinations=combinations,
            quote_status='mock', message='테스트 데이터 기반 조합입니다. 실시간 검색·예약 불가.',
            gemini_called=called, prompt_tokens=prompt_tokens, output_tokens=output_tokens,
            transport_options=transports, hotel_options=hotels,
            itinerary=recommend_itinerary(search_requirements), assumed_return_date=assumed_return)
