"""ADK 에이전트 정의(빈 껍데기).

instruction·도구·출력 스키마(`TravelPackage`)는 에이전트 담당자가 채운다.
모델 이름은 환경 변수 `GEMINI_MODEL`로 받는다.
"""

from google.adk.agents import LlmAgent

from trupass_agent.config import Settings

AGENT_NAME = "trupass_planner"


def build_root_agent(settings: Settings) -> LlmAgent:
    if not settings.gemini_model:
        raise ValueError("GEMINI_MODEL 환경 변수가 비어 있다")
    return LlmAgent(
        name=AGENT_NAME,
        model=settings.gemini_model,
        description="자연어 여행 요청을 받아 항공·숙소·체험 오퍼를 조합한 패키지 2~3안을 만든다.",
        instruction="",
        tools=[],
    )
