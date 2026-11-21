# TRUPASS Agent

| 구성 | 용도 | 로컬 포트 |
| --- | --- | --- |
| `src/trupass_agent/` | Spring Core가 사용하는 Google ADK 에이전트 | 8001 |
| `trupass-ai/` | Gemini로 여행 조건을 추출하고 mock-merchant 오퍼를 조합하는 별도 FastAPI 플래너 | 8002 |

기존 ADK 에이전트 실행 방법은 [루트 README](../README.md)를 참고한다. 아래 플래너는 자체 가상환경과 의존성 파일을 사용한다. 기존 Docker 이미지에는 포함되지 않으며 Core의 `/compose` 호출에도 연결되어 있지 않다.

## 옮긴 코드와 변경 사항

- 로컬 `trupass-ai`의 앱, 테스트, 의존성, 환경변수 예제를 `agent/trupass-ai/`에 복사했다. 개인 키가 들어 있는 `.env`와 가상환경은 별도로 관리한다.
- `TRUPASS_PROVIDER_MODE=mock-merchant`로 실행하면 Spring 판매자 A의 항공편과 판매자 B의 숙소를 `127.0.0.1:9001`에서 조회한다. 응답에는 카탈로그 오퍼 ID와 금액이 들어간다. `mock` 모드는 Python 내부의 가상 데이터를 사용한다.
- Gemini는 자연어 조건 추출에 사용한다. 오퍼 조합과 예산 계산은 Python에서 처리한다. 같은 날 같은 요청의 추출 결과는 메모리 캐시에서 재사용할 수 있다.

## 로컬 실행

루트에서 PowerShell 터미널을 열고 mock-merchant를 실행한다.

```powershell
$env:SERVER_ADDRESS = '127.0.0.1'
.\gradlew.bat :mock-merchant:bootRun
```

별도 PowerShell 터미널에서 플래너를 실행한다. 팀 실행 환경은 Python 3.14를 사용한다. `uv`가 없으면 `winget install --id=astral-sh.uv -e`로 설치한 뒤 PowerShell을 새로 연다. `trupass-ai/pyproject.toml`은 플래너의 의존성을 선언하고, 같은 폴더의 `uv.lock`은 설치 버전을 고정한다. 상위 `agent/uv.lock`은 기존 ADK 에이전트용이다.

```powershell
Set-Location agent/trupass-ai
uv sync --locked --python 3.14
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# .env에 로컬 키를 설정한 뒤 실행
$env:TRUPASS_PROVIDER_MODE = 'mock-merchant'
uv run --locked --python 3.14 uvicorn app.main:app --host 127.0.0.1 --port 8002
```

`.env`에 `GEMINI_API_KEY`와 `INTERNAL_API_KEY`를 설정한다. 모델은 코드 기본값을 사용하거나 해당 키로 사용할 수 있는 모델을 `GEMINI_MODEL`에 지정한다.

`GET /health`의 `provider`가 `mock-merchant`인지 확인한다. 요청은 [로컬 API 문서](http://127.0.0.1:8002/docs)에서 실행할 수 있다. `POST /api/v1/trips/plan`의 `X-Internal-API-Key` 헤더에는 로컬 `.env`의 내부 키를 넣는다.

```json
{
  "prompt": "12월 18일쯤 인천에서 도쿄, 아침 출발, 성인 2명",
  "accommodation_prompt": "미술관 근처 조용한 호텔",
  "purpose_prompt": "미술관과 건축물 탐방"
}
```

세 입력은 Gemini 한 번에 전달한다. 응답의 `transport_options`, `hotel_options`, `itinerary`가 각 추천 목록이다. 귀국일이 없으면 검색에만 2박을 가정하고 `assumed_return_date`에 표시한다. 예산을 입력하면 `combinations`에서 예산 기준 조합도 확인할 수 있다.

## 활용 범위

- 로컬 mock-merchant 모드에서 인천 출발 도쿄·오사카·후쿠오카 왕복 항공과 숙소를 추천한다. 플래너 내부의 가상 항공 판매자 3곳과 숙소 판매자 3곳을 제공하고, 오사카·후쿠오카에는 Spring의 기존 오퍼도 포함한다. Spring 숙소 오퍼는 2박 조건에만 포함된다.
- 항공 출발 시간과 가상 숙소 요금은 데모 값이다. 가상 숙소는 객실당 최대 2명을 가정하고 숙박 일수와 객실 수로 가격을 계산한다. Spring 숙소 금액은 기존 2박 오퍼 금액을 그대로 사용한다.
- 일정은 도쿄·오사카·후쿠오카의 사전 작성된 미술·건축, 미식, 일반 관광 코스에서 추천한다. 운영 시간과 이동 시간은 검증하지 않는다. 현지 교통비와 기타 비용은 고정 추정치다. 재고 조회, 예약, 결제, 실시간 견적은 지원하지 않는다.
- `POST /api/v1/trips/reprice`는 요청에 포함된 금액으로 예산을 다시 계산한다. 결제 금액이나 확정 견적에는 사용할 수 없다.
- 서버는 로컬에서 실행된다. 자연어 파싱은 캐시 미스 시 Google Gemini API를 호출하므로 인터넷 연결과 유효한 키가 필요하다. 기존 테스트는 외부 Gemini 호출 없이 실행된다.

```powershell
# agent/trupass-ai에서 실행
uv run --locked --python 3.14 pytest -q
```
