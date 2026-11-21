from datetime import date, timedelta
from app.core.cache import TTLCache
from app.domain.models import TripInput, TripRequirements, TransportKind
from app.providers.mock import MockTravelProvider
from app.services.budget import BudgetCalculator
from app.services.combiner import CombinationEngine
from app.services.planner import TripPlanner


class FakeAnalyzer:
    def __init__(self, requirements):
        self.requirements = requirements
        self.calls = 0

    def analyze(self, request):
        self.calls += 1
        return self.requirements, False, 0, 0


def test_budget_warning_and_breakdown():
    value = BudgetCalculator.calculate(500_000, 230_000, 250_000, 50_000, 80_000)
    assert value.total_krw == 610_000
    assert value.over_budget_krw == 110_000
    assert value.remaining_budget_krw == 0
    assert value.is_over_budget


def test_cache_ttl_and_bounded_entries():
    cache = TTLCache(600, 1)
    cache.put('a', 'one')
    cache.put('b', 'two')
    assert cache.get('a') is None
    assert cache.get('b') == 'two'


def test_missing_required_fields_do_not_search():
    analyzer = FakeAnalyzer(TripRequirements(destination='부산'))
    plan = TripPlanner(analyzer, MockTravelProvider(), CombinationEngine(BudgetCalculator()))
    response = plan.plan(TripInput(prompt='부산으로 여행가고 싶습니다'))
    assert 'origin' in response.missing_fields
    assert 'budget_krw' not in response.missing_fields
    assert response.combinations == []


def test_three_combinations_and_hotel_date_alignment():
    departure = date.today() + timedelta(days=15)
    return_date = departure + timedelta(days=3)
    req = TripRequirements(origin='서울', destination='부산', departure_date=departure,
                           return_date=return_date, travelers=2, budget_krw=1_000_000)
    plan = TripPlanner(FakeAnalyzer(req), MockTravelProvider(), CombinationEngine(BudgetCalculator()))
    result = plan.plan(TripInput(prompt='서울에서 부산으로 친구와 여행할래요'))
    assert len(result.combinations) == 3
    assert len(set(x.id for x in result.combinations)) == 3
    assert all(x.transport.departure_date == x.hotel.checkin for x in result.combinations)
    assert all(x.transport.return_date == x.hotel.checkout for x in result.combinations)
    assert result.quote_status == 'mock'
    assert all(not c.transport.is_live and not c.hotel.is_live for c in result.combinations)


def test_preferred_transport_is_respected():
    departure = date.today() + timedelta(days=9)
    req = TripRequirements(origin='서울', destination='부산', departure_date=departure,
                           return_date=departure + timedelta(days=2), budget_krw=800000,
                           transport_preferences=[TransportKind.TRAIN])
    result = TripPlanner(FakeAnalyzer(req), MockTravelProvider(),
                         CombinationEngine(BudgetCalculator())).plan(TripInput(prompt='KTX로 부산 여행가려고 해'))
    assert all(c.transport.kind == TransportKind.TRAIN for c in result.combinations)


def test_mismatched_hotel_dates_are_excluded():
    departure = date.today() + timedelta(days=7)
    req = TripRequirements(origin='서울', destination='부산', departure_date=departure,
                           return_date=departure + timedelta(days=2), budget_krw=800000)
    provider = MockTravelProvider()
    transport = provider.search_transport(req)[0]
    hotel = provider.search_hotels(req, transport)[0]
    hotel.checkout = hotel.checkout + timedelta(days=1)
    assert CombinationEngine(BudgetCalculator()).combine(req, [transport], {transport.id: [hotel]}) == []


def test_tokyo_three_prompts_recommend_distinct_sellers_without_budget_or_return_date():
    from app.providers.mock_merchant import MockMerchantProvider

    departure = date.today() + timedelta(days=15)
    analyzer = FakeAnalyzer(TripRequirements(
        origin='인천', destination='도쿄', departure_date=departure, travelers=2,
        departure_period='morning', accommodation_preferences=['미술관 근처', '조용한'],
        purpose='미술관과 건축물 탐방',
    ))
    planner = TripPlanner(analyzer, MockMerchantProvider(), CombinationEngine(BudgetCalculator()))
    result = planner.plan(TripInput(
        prompt='인천에서 도쿄로 아침 출발, 성인 2명',
        accommodation_prompt='미술관 근처 조용한 호텔',
        purpose_prompt='미술관과 건축물 탐방',
    ))
    assert analyzer.calls == 1
    assert result.missing_fields == []
    assert result.requirements.return_date is None
    assert result.assumed_return_date == departure + timedelta(days=2)
    assert len({option.seller for option in result.transport_options}) == 3
    assert [option.departure_time for option in result.transport_options] == ['06:50', '08:40', '10:20']
    assert len({option.seller for option in result.hotel_options}) == 3
    assert result.hotel_options[0].seller == '스테이픽'
    assert result.itinerary[0].area == '우에노'
    assert result.combinations == []
