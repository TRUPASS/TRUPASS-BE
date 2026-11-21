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
        req, called, prompt_tokens, output_tokens = self._analyzer.analyze(request)
        missing = [field for field in ('origin', 'destination', 'departure_date')
                   if getattr(req, field) is None]
        if req.departure_date and req.departure_date < datetime.now(ZoneInfo("Asia/Seoul")).date():
            missing.append('future_departure_date')
        if missing:
            return PlanResponse(requirements=req, missing_fields=missing, combinations=[],
                quote_status='mock', message='필수 여행 조건이 부족하거나 날짜가 과거입니다.',
                gemini_called=called, prompt_tokens=prompt_tokens, output_tokens=output_tokens)
        assumed_return = None
        search_req = req
        if req.return_date is None and req.departure_date is not None:
            assumed_return = req.departure_date + timedelta(days=2)
            search_req = req.model_copy(update={'return_date': assumed_return})
        transports = self._provider.search_transport(search_req)
        # All current transport options share the same destination and travel dates.
        hotels = self._provider.search_hotels(search_req, transports[0]) if transports else []
        options = self._combiner.combine(search_req, transports, {t.id: hotels for t in transports})
        return PlanResponse(requirements=req, missing_fields=[], combinations=options,
            quote_status='mock', message='테스트 데이터 기반 조합입니다. 실시간 검색·예약 불가.',
            gemini_called=called, prompt_tokens=prompt_tokens, output_tokens=output_tokens,
            transport_options=transports, hotel_options=hotels,
            itinerary=recommend_itinerary(search_req), assumed_return_date=assumed_return)
