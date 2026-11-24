"""8001 플래너에서 자연어 여행 조건을 추출하는 ADK 에이전트."""

import asyncio

from app.domain.models import TripRequirements
from app.services.analyzer import SYSTEM_INSTRUCTION, GeminiAnalyzer
from google.adk.agents import LlmAgent
from google.adk.models.google_llm import Gemini
from google.adk.runners import Runner
from google.adk.sessions import InMemorySessionService
from google.genai import types
from pydantic import SecretStr

from trupass_agent.config import Settings

AGENT_NAME = "trupass_planner"


def build_root_agent(settings: Settings) -> LlmAgent:
    if not settings.gemini_model:
        raise ValueError("GEMINI_MODEL 환경 변수가 비어 있다")
    key = settings.google_api_key
    return LlmAgent(
        name=AGENT_NAME,
        model=Gemini(model=settings.gemini_model, client_kwargs={"api_key": key.get_secret_value()})
        if key
        else settings.gemini_model,
        description="여행 요청에서 검색에 필요한 조건을 추출한다.",
        instruction=SYSTEM_INSTRUCTION,
        output_schema=TripRequirements,
        generate_content_config=types.GenerateContentConfig(temperature=0, max_output_tokens=600),
    )


class AdkTripAnalyzer(GeminiAnalyzer):
    def _extract(self, user_text: str) -> tuple[TripRequirements, int | None, int | None]:
        # FastAPI는 동기 추출 엔드포인트를 작업 스레드에서 실행한다.
        return asyncio.run(self._run_agent(user_text))

    async def _run_agent(self, user_text: str) -> tuple[TripRequirements, int | None, int | None]:
        settings = Settings(gemini_model=self._model, google_api_key=SecretStr(self._key))
        agent = build_root_agent(settings)
        message = types.Content(role="user", parts=[types.Part(text=user_text)])
        # 각 요청은 자체 세션을 갖는다. 요청 간 재사용은 상위 추출기의 TTL 캐시가 담당한다.
        async with Runner(
            agent=agent, app_name=AGENT_NAME, session_service=InMemorySessionService(), auto_create_session=True
        ) as runner:
            result = None
            async for event in runner.run_async(user_id="planner", session_id="request", new_message=message):
                if event.error_code:
                    raise RuntimeError("ADK가 여행 조건을 추출하지 못했습니다.")
                if event.is_final_response() and event.content:
                    text = "".join(p.text or "" for p in event.content.parts or [] if not p.thought)
                    if text:
                        usage = event.usage_metadata
                        result = (
                            TripRequirements.model_validate_json(text),
                            usage.prompt_token_count if usage else None,
                            usage.candidates_token_count if usage else None,
                        )
        if result is None:
            raise RuntimeError("ADK 여행 조건 응답이 비어 있습니다.")
        return result
