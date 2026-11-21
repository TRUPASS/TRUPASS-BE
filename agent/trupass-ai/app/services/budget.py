from app.domain.models import MoneyBreakdown


class BudgetCalculator:
    @staticmethod
    def calculate(budget_krw: int, transport_krw: int, hotel_krw: int,
                  local_transport_krw: int, other_krw: int) -> MoneyBreakdown:
        values = [budget_krw, transport_krw, hotel_krw, local_transport_krw, other_krw]
        if budget_krw <= 0 or any(value < 0 for value in values):
            raise ValueError('Budget must be positive and prices nonnegative')
        total = transport_krw + hotel_krw + local_transport_krw + other_krw
        return MoneyBreakdown(transport_krw=transport_krw, hotel_krw=hotel_krw,
            estimated_local_transport_krw=local_transport_krw,
            estimated_other_krw=other_krw, total_krw=total,
            over_budget_krw=max(0, total-budget_krw),
            remaining_budget_krw=max(0, budget_krw-total), is_over_budget=total>budget_krw)
