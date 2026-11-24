from collections.abc import AsyncGenerator
from datetime import date

import httpx
import pytest
from app.core.config import get_settings as planner_settings
from fastapi.testclient import TestClient
from google.adk.models.google_llm import Gemini
from google.adk.models.llm_request import LlmRequest
from google.adk.models.llm_response import LlmResponse
from google.genai import types
from pydantic import ValidationError

from trupass_agent.agent import AGENT_NAME, build_root_agent
from trupass_agent.config import Settings
from trupass_agent.main import app
from trupass_agent.schemas import OfferCategory, OfferRef, PackageOption, TravelPackage
from trupass_agent.tools.core_client import CoreApiError, CoreClient, SearchOffersRequest


def _offer(offer_id: str, amount: int, currency: str = "KRW") -> OfferRef:
    return OfferRef(
        offer_id=offer_id,
        merchant_id="A",
        category=OfferCategory.FLIGHT,
        title=offer_id,
        amount=amount,
        currency=currency,
    )


def test_health() -> None:
    response = TestClient(app).get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_plan_runs_adk_and_caches_extraction(monkeypatch: pytest.MonkeyPatch) -> None:
    calls = []

    async def reply(_model: Gemini, llm_request: LlmRequest, stream: bool = False) -> AsyncGenerator[LlmResponse]:
        calls.append(llm_request)
        yield LlmResponse(content=types.Content(role="model", parts=[types.Part(text='{"destination":"도쿄"}')]))

    monkeypatch.setattr(Gemini, "generate_content_async", reply)
    monkeypatch.setattr(app.state.analyzer, "_key", "test")
    monkeypatch.setattr(planner_settings(), "internal_api_key", "internal-test")
    with TestClient(app) as client:
        for expected_call in (True, False):
            response = client.post(
                "/api/v1/trips/plan",
                json={"prompt": "도쿄로 여행할래요"},
                headers={"X-Internal-API-Key": "internal-test"},
            )
            assert response.status_code == 200
            assert response.json()["requirements"] == {"destination": "도쿄"}
            assert response.json()["gemini_called"] is expected_call
    assert len(calls) == 1


def test_package_total_is_derived_from_offers_and_serialized_camel_case() -> None:
    option = PackageOption(option_id="o1", title="t", summary="s", offers=[_offer("a", 100), _offer("b", 250)])
    dumped = option.model_dump(by_alias=True)
    assert dumped["totalAmount"] == 350
    assert dumped["currency"] == "KRW"
    assert dumped["offers"][0]["offerId"] == "a"


def test_package_rejects_mixed_currency() -> None:
    with pytest.raises(ValidationError):
        PackageOption(option_id="o1", title="t", summary="s", offers=[_offer("a", 1), _offer("b", 1, "USD")])


def test_travel_package_requires_two_to_three_options() -> None:
    option = PackageOption(option_id="o", title="t", summary="s", offers=[_offer("a", 1)])
    TravelPackage(request_summary="r", options=[option, option])
    with pytest.raises(ValidationError):
        TravelPackage(request_summary="r", options=[option])


def test_build_root_agent_uses_model_from_settings() -> None:
    agent = build_root_agent(Settings(gemini_model="gemini-test-model"))
    assert agent.name == AGENT_NAME
    assert agent.model == "gemini-test-model"
    with pytest.raises(ValueError):
        build_root_agent(Settings(gemini_model=""))


async def _search(transport: httpx.MockTransport) -> list[OfferRef]:
    async with CoreClient("http://core.test", transport=transport) as client:
        response = await client.search_offers(
            SearchOffersRequest(
                destination="Osaka", start_date=date(2026, 12, 1), end_date=date(2026, 12, 3), travelers=2
            )
        )
        return response.offers


def test_core_client_calls_internal_search_offers() -> None:
    import asyncio

    seen: dict[str, object] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["path"] = request.url.path
        seen["body"] = request.read().decode()
        return httpx.Response(
            200,
            json={
                "isSuccess": True,
                "code": "COMMON200",
                "message": "성공했습니다.",
                "result": {
                    "offers": [
                        {
                            "offerId": "A-1",
                            "merchantId": "A",
                            "category": "FLIGHT",
                            "title": "ICN-KIX",
                            "amount": 320000,
                            "currency": "KRW",
                        }
                    ]
                },
            },
        )

    offers = asyncio.run(_search(httpx.MockTransport(handler)))
    assert seen["path"] == "/internal/tools/search-offers"
    assert '"startDate":"2026-12-01"' in str(seen["body"])
    assert offers[0].offer_id == "A-1"
    assert offers[0].amount == 320000


def test_core_client_raises_core_error_code_on_failure() -> None:
    import asyncio

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            501, json={"isSuccess": False, "code": "COMMON501", "message": "아직 구현되지 않은 기능입니다."}
        )

    with pytest.raises(CoreApiError) as error:
        asyncio.run(_search(httpx.MockTransport(handler)))
    assert error.value.status_code == 501
    assert error.value.code == "COMMON501"


def test_main_routes_offer_extraction_only() -> None:
    paths = TestClient(app).get("/openapi.json").json()["paths"]
    assert "/api/v1/trips/plan" in paths
    assert "/api/v1/trips/prompts" in paths
    assert "/compose" not in paths
