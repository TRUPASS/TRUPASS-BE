"""여행 프롬프트 관리 및 조건 추출 서버(포트 8001)."""

import uvicorn
from app.bootstrap import install_planner
from fastapi import FastAPI

from trupass_agent.agent import AdkTripAnalyzer

app = FastAPI(title="Trupass Agent", version="0.1.0")
install_planner(app, AdkTripAnalyzer)


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


def run() -> None:
    uvicorn.run("trupass_agent.main:app", host="0.0.0.0", port=8001)  # 컨테이너 내부 바인딩


if __name__ == "__main__":
    run()
