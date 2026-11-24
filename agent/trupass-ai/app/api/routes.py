import hmac
from io import BytesIO
from uuid import UUID
from zipfile import ZIP_DEFLATED, ZipFile
import httpx
from google.genai.errors import APIError
from fastapi import APIRouter, Depends, Header, HTTPException, Request, Response
from app.core.config import get_settings
from app.domain.models import PromptUpdate, SavedPrompts, PlanResponse, RepriceRequest, MoneyBreakdown, TripInput
from app.services.budget import BudgetCalculator

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


@router.post('/trips/prompts/{prompt_id}/export', response_class=Response,
             responses={200: {'content': {'application/zip': {'schema': {'type': 'string', 'format': 'binary'}}}}},
             dependencies=[Depends(require_internal_key)], summary='여행·숙소·목적 조건을 JSON 파일 3개로 다운로드')
def export_prompts(prompt_id: UUID, request: Request) -> Response:
    prompts = get_prompts(prompt_id, request).prompts
    sections = (
        ('travel.json', TripInput(prompt=prompts.prompt, origin_hint=prompts.origin_hint)),
        ('accommodation.json', TripInput(accommodation_prompt=prompts.accommodation_prompt)),
        ('purpose.json', TripInput(purpose_prompt=prompts.purpose_prompt)),
    )
    buffer = BytesIO()
    with ZipFile(buffer, 'w', compression=ZIP_DEFLATED) as archive:
        for filename, section in sections:
            content = '{}'
            if any((section.prompt, section.origin_hint, section.accommodation_prompt, section.purpose_prompt)):
                conditions, *_ = _analyze(request, section, prompt_id)
                content = conditions.model_dump_json(indent=2, exclude_none=True)
            archive.writestr(filename, content + '\n')
    return Response(buffer.getvalue(), media_type='application/zip', headers={
        'Content-Disposition': f'attachment; filename="trip-{prompt_id}.zip"',
        'Cache-Control': 'no-store',
    })


@router.post('/trips/plan', response_model=PlanResponse, response_model_exclude_none=True,
             dependencies=[Depends(require_internal_key)], summary='저장된 여행 프롬프트에서 검색 조건 추출')
def plan_trip(update: PromptUpdate, request: Request) -> PlanResponse:
    saved = save_prompts(update, request)
    conditions, called, inputs, outputs = _analyze(request, saved.prompts, saved.prompt_id)
    return PlanResponse(prompt_id=saved.prompt_id, requirements=conditions, gemini_called=called,
                        prompt_tokens=inputs, output_tokens=outputs)


@router.post('/trips/reprice', response_model=MoneyBreakdown, dependencies=[Depends(require_internal_key)])
def reprice(request: RepriceRequest):
    return BudgetCalculator.calculate(request.budget_krw, request.transport_krw,
        request.hotel_krw, request.estimated_local_transport_krw,
        request.estimated_other_krw)
