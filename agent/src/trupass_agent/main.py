"""agent HTTP 서버(FastAPI, 포트 8001)."""

import uvicorn
from fastapi import FastAPI

app = FastAPI(title="Trupass Agent", version="0.1.0")


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


def run() -> None:
    uvicorn.run("trupass_agent.main:app", host="0.0.0.0", port=8001)  # 컨테이너 내부 바인딩


if __name__ == "__main__":
    run()
