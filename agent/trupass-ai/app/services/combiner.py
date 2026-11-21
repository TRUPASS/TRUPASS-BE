"""Deterministic combinations: zero LLM tokens and reproducible budget checks."""
from app.domain.models import TripRequirements, TripCombination, TransportOption, HotelOption
from app.services.budget import BudgetCalculator


class CombinationEngine:
    def __init__(self, calculator: BudgetCalculator):
        self._calculator = calculator

    def combine(self, req: TripRequirements, transports: list[TransportOption],
                hotels_by_transport: dict[str, list[HotelOption]]) -> list[TripCombination]:
        if req.budget_krw is None:
            return []
        candidates: list[TripCombination] = []
        # Estimates are explicitly marked as placeholders, not quoted prices.
        nights = (req.return_date - req.departure_date).days  # validated upstream
        estimated_local = 20_000 * nights * req.travelers
        estimated_other = 45_000 * nights * req.travelers
        for t in transports:
            for h in hotels_by_transport.get(t.id, []):
                if h.checkin != t.departure_date or h.checkout != t.return_date:
                    continue  # Never pair a hotel booked for the wrong dates.
                total = self._calculator.calculate(req.budget_krw, t.total_price_krw,
                    h.total_price_krw, estimated_local, estimated_other)
                candidates.append(TripCombination(
                    id=f'{t.id}--{h.id}', transport=t, hotel=h, budget=total,
                    strategy='balanced', notes=['숙소·교통요금은 mock 데이터입니다.',
                        '현지 교통·식비/관광비는 임시 추정치로 실제 비용과 다릅니다.']))
        if not candidates:
            return []
        # Diversify 3 proposals: cheapest, median-cost, more comfort/shorter travel.
        cheapest = min(candidates, key=lambda c: (c.budget.total_krw, c.transport.duration_minutes))
        balanced = min(candidates, key=lambda c: (
            abs(c.budget.total_krw - int(req.budget_krw * .85)), c.transport.duration_minutes))
        comfort = min(candidates, key=lambda c: (
            c.transport.duration_minutes, -c.hotel.total_price_krw, c.budget.total_krw))
        result = []
        for item, strategy in [(cheapest, 'budget'), (balanced, 'balanced'), (comfort, 'comfort')]:
            if item.id not in {x.id for x in result}:
                result.append(item.model_copy(update={'strategy': strategy}))
        # Fill remaining slots if 2 strategies chose the same item.
        for candidate in sorted(candidates, key=lambda c: c.budget.total_krw):
            if len(result) >= 3:
                break
            if candidate.id not in {x.id for x in result}:
                result.append(candidate.model_copy(update={'strategy': 'balanced'}))
        return result[:3]
