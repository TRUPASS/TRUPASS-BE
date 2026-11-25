# TRUPASS Agent · 여행 조건 추출 (8001)

세 질문의 원문은 서로 다른 필드로 메모리에 보관한다. Gemini는 저장한 답변에서 검색 조건을 추출한다. BE가 이 JSON을 사용해 후기 평점 필터, 상품 검색, 조합을 수행한다.

## 실행

저장소 루트에서 실행한다. 환경 파일에 `GEMINI_API_KEY`(또는 `GOOGLE_API_KEY`), `GEMINI_MODEL`, `INTERNAL_API_KEY`를 설정한다.

```powershell
Set-Location agent
uv sync --locked --python 3.14
uv run --locked --python 3.14 uvicorn trupass_agent.main:app --env-file "$env:USERPROFILE\Desktop\trupass-ai\.env" --host 127.0.0.1 --port 8001
```

## Swagger에서 사용하기

`POST /api/v1/trips/export`에 아래 예시의 세 답변과 내부 키를 넣고 실행하면 Swagger의 **Server response → Response body**에 통합 JSON이 표시된다. 서버가 자동 생성한 ID는 응답 헤더 `X-Prompt-ID`에서 확인할 수 있다.

[로컬 Swagger](http://127.0.0.1:8001/docs)에서 각 요청의 `X-Internal-API-Key` 헤더에 내부 키를 넣는다.

1. `POST /api/v1/trips/prompts`에 여행·숙소·목적 중 하나 이상을 보내면 Gemini 호출 없이 `prompt_id`가 발급된다.
2. 같은 API에 `prompt_id`와 변경할 필드만 보내면 해당 답변을 교체한다. 빈 문자열이나 null은 해당 필드를 비운다. `GET /api/v1/trips/prompts/{prompt_id}`로 현재 답변을 확인한다.
3. `POST /api/v1/trips/plan`에 `prompt_id`를 보내면 저장된 답변을 분석한다. 처음부터 세 답변을 이 API에 직접 보내도 된다.
4. 통합 결과를 파일로 받으려면 `POST /api/v1/trips/prompts/{prompt_id}/export`에 같은 ID를 입력한다. 세 답변을 한 번에 분석한 조건을 `trip-{prompt_id}.json`으로 내려받는다. 같은 입력은 /plan과 추출 캐시를 공유한다.

```json
{
  "prompt": "도쿄에 가고 싶어",
  "accommodation_prompt": "실제 여행자 평점 4.5 이상인 숙소",
  "purpose_prompt": "가족과 대화할 시간이 가장 중요해. 함께 푹 쉬고 싶어."
}
```

예상되는 requirements에는 destination, min_traveler_rating(4.5), core_travel_goal, desired_satisfaction 등이 들어간다. 인원이나 예산을 말하지 않으면 해당 키는 생략한다. 평점은 BE에 전달할 최소 조건이며, 실제 후기 조회는 BE에서 수행한다.

[검색 조건 스키마와 추출 기준](trupass-ai/app/prompts/extract_trip_requirements.md)을 모델 지시문으로 직접 읽는다. 필드를 추가하면 `app/domain/models.py`의 TripRequirements 검증 규칙도 함께 갱신한다.

원문은 서버 프로세스 메모리에 저장되며 재시작 시 초기화된다. 기본 보관 시간은 마지막 저장 후 600초, 최대 256건이다(`PROMPT_TTL_SECONDS`, `PROMPT_MAX_ENTRIES`). 여러 프로세스는 메모리를 공유하지 않는다. 추출 결과 캐시는 독립적이며 세 답변의 조합이 바뀌면 새로 분석한다.

## BE 연동 변경

기존 패키지 생성 경로 `/compose`는 제거했다. Core에서 해당 경로를 사용하는 호출자는 내부 키와 함께 `/api/v1/trips/plan`을 호출하고 응답의 `requirements`를 읽도록 변경해야 한다. `/api/v1/trips/reprice`는 입력 금액의 합계와 예산 차이를 계산한다.

```powershell
# agent에서 실행
uv run --locked --python 3.14 pytest -q tests trupass-ai/tests
```
