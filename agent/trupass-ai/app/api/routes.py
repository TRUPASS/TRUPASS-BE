import hmac
from fastapi import APIRouter, Depends, Header, HTTPException, Request
from app.core.config import get_settings
from app.domain.models import TripInput, PlanResponse, RepriceRequest, MoneyBreakdown
from app.services.budget import BudgetCalculator
from app.services.planner import TripPlanner

router = APIRouter(prefix='/api/v1')


def require_internal_key(x_internal_api_key: str | None = Header(default=None)):
    expected = get_settings().internal_api_key
    if not expected:
        raise HTTPException(status_code=503, detail='Server authorization is not configured')
    if not x_internal_api_key or not hmac.compare_digest(x_internal_api_key, expected):
        raise HTTPException(status_code=401, detail='Unauthorized')


def get_planner(request: Request) -> TripPlanner:
    return request.app.state.planner


@router.post('/trips/plan', response_model=PlanResponse, dependencies=[Depends(require_internal_key)])
def plan_trip(request: TripInput, planner: TripPlanner = Depends(get_planner)):
    try:
        return planner.plan(request)
    except RuntimeError:
        # No raw vendor errors or keys in public response.
        raise HTTPException(status_code=503, detail='AI provider temporarily unavailable')


@router.post('/trips/reprice', response_model=MoneyBreakdown, dependencies=[Depends(require_internal_key)])
def reprice(request: RepriceRequest):
    return BudgetCalculator.calculate(request.budget_krw, request.transport_krw,
        request.hotel_krw, request.estimated_local_transport_krw,
        request.estimated_other_krw)
