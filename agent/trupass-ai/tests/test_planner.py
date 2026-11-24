from app.core.cache import TTLCache
from app.services.budget import BudgetCalculator


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
