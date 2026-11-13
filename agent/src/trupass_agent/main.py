"""agent HTTP 서버(FastAPI, 포트 8001).

core(trip)만 이 서버를 부른다. 에이전트는 외부 API를 직접 부르지 않고,
필요한 데이터는 core의 `/internal/tools/*`로 얻는다.
"""

import uvicorn
from fastapi import FastAPI, HTTPException, status

from trupass_agent.schemas import ComposeRequest, TravelPackage

app = FastAPI(title="Trupass Agent", version="0.1.0")


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/compose", response_model=TravelPackage, response_model_by_alias=True)
async def compose(request: ComposeRequest) -> TravelPackage:
    """자연어 요청으로 패키지 2~3안을 만든다. 에이전트 담당자가 ADK 실행을 연결할 때까지 501을 돌려준다."""
    raise HTTPException(status_code=status.HTTP_501_NOT_IMPLEMENTED, detail="compose is not implemented yet")


def run() -> None:
    uvicorn.run("trupass_agent.main:app", host="0.0.0.0", port=8001)  # 컨테이너 내부 바인딩


if __name__ == "__main__":
    run()
