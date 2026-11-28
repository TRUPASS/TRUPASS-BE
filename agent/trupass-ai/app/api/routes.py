import hmac
from uuid import UUID
import httpx
from google.genai.errors import APIError
from fastapi import APIRouter, Depends, Header, HTTPException, Request, Response
from pydantic import ValidationError
from app.core.config import get_settings
from app.domain.models import PromptUpdate, SavedPrompts, PlanResponse, RepriceRequest, MoneyBreakdown, TripInput, TripRequirements
from app.domain.recommend import RecommendRequest, RecommendResponse
from app.services.budget import BudgetCalculator
from app.services.recommender import InvalidRecommendation

router = APIRouter(prefix='/api/v1')


def require_internal_key(x_internal_api_key: str | None = Header(default=None)):
    expected = get_settings().internal_api_key
    if not expected:
        raise HTTPException(status_code=503, detail='Server authorization is not configured')
    if not x_internal_api_key or not hmac.compare_digest(x_internal_api_key, expected):
        raise HTTPException(status_code=401, detail='Unauthorized')


@router.post('/trips/prompts', response_model=SavedPrompts, response_model_exclude_none=True,
             dependencies=[Depends(require_internal_key)])
def save_prompts(update: PromptUpdate, request: Request) -> SavedPrompts:
    try:
        return request.app.state.prompt_memory.save(update)
    except KeyError:
        raise HTTPException(404, '저장된 프롬프트가 없거나 만료되었습니다.') from None
    except ValueError as exc:
        raise HTTPException(422, str(exc)) from None


@router.get('/trips/prompts/{prompt_id}', response_model=SavedPrompts, response_model_exclude_none=True,
            dependencies=[Depends(require_internal_key)])
def get_prompts(prompt_id: UUID, request: Request) -> SavedPrompts:
    try:
        return request.app.state.prompt_memory.get(prompt_id)
    except KeyError:
        raise HTTPException(404, '저장된 프롬프트가 없거나 만료되었습니다.') from None


def _analyze(request: Request, prompts: TripInput, prompt_id: UUID):
    try:
        return request.app.state.analyzer.analyze(prompts)
    except (APIError, httpx.RequestError, RuntimeError, ValueError):
        raise HTTPException(503, detail={'message': '조건 추출에 실패했습니다. 저장한 프롬프트로 다시 시도해 주세요.',
                                        'prompt_id': str(prompt_id)}) from None


@router.post('/trips/prompts/{prompt_id}/export', response_model=TripRequirements,
             response_model_exclude_none=True, dependencies=[Depends(require_internal_key)],
             summary='여행·숙소·목적 조건을 하나의 JSON 파일로 다운로드')
def export_prompts(prompt_id: UUID, request: Request) -> Response:
    return _json_file(get_prompts(prompt_id, request), request)


@router.post('/trips/export', response_model=TripRequirements, response_model_exclude_none=True,
             dependencies=[Depends(require_internal_key)], summary='세 프롬프트의 추출 결과를 JSON으로 보기')
def export_trip(prompts: TripInput, request: Request, response: Response) -> TripRequirements:
    saved = save_prompts(PromptUpdate(**prompts.model_dump(exclude_unset=True)), request)
    conditions, *_ = _analyze(request, saved.prompts, saved.prompt_id)
    response.headers['X-Prompt-ID'] = str(saved.prompt_id)
    response.headers['Cache-Control'] = 'no-store'
    return conditions


def _json_file(saved: SavedPrompts, request: Request) -> Response:
    prompt_id = saved.prompt_id
    conditions, *_ = _analyze(request, saved.prompts, prompt_id)
    return Response(conditions.model_dump_json(indent=2, exclude_none=True), media_type='application/json', headers={
        'Content-Disposition': f'attachment; filename="trip-{prompt_id}.json"',
        'X-Prompt-ID': str(prompt_id),
        'Cache-Control': 'no-store',
    })


@router.post('/trips/plan', response_model=PlanResponse, response_model_exclude_none=True,
             dependencies=[Depends(require_internal_key)], summary='저장된 여행 프롬프트에서 검색 조건 추출')
def plan_trip(update: PromptUpdate, request: Request) -> PlanResponse:
    saved = save_prompts(update, request)
    conditions, called, inputs, outputs = _analyze(request, saved.prompts, saved.prompt_id)
    return PlanResponse(prompt_id=saved.prompt_id, requirements=conditions, gemini_called=called,
                        prompt_tokens=inputs, output_tokens=outputs)


@router.post('/trips/recommend', response_model=RecommendResponse, response_model_exclude_none=True,
             dependencies=[Depends(require_internal_key)], summary='core가 고른 항공·숙소 후보로 추천 조합 만들기')
def recommend_trip(body: RecommendRequest, request: Request) -> RecommendResponse:
    try:
        recommendation, called, inputs, outputs = request.app.state.recommender.recommend(body)
    except (InvalidRecommendation, ValidationError):
        raise HTTPException(502, detail='AI가 후보에 없는 조합이나 올바르지 않은 형식을 돌려줬습니다.') from None
    except (APIError, httpx.RequestError, RuntimeError, ValueError):
        raise HTTPException(503, detail='조합 추천에 실패했습니다. 다시 시도해 주세요.') from None
    return RecommendResponse(**recommendation.model_dump(), gemini_called=called,
                             prompt_tokens=inputs, output_tokens=outputs)


@router.post('/trips/reprice', response_model=MoneyBreakdown, dependencies=[Depends(require_internal_key)])
def reprice(request: RepriceRequest):
    return BudgetCalculator.calculate(request.budget_krw, request.transport_krw,
        request.hotel_krw, request.estimated_local_transport_krw,
        request.estimated_other_krw)
