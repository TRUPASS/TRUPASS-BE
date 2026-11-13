# TRUPASS-BE

Trupass 백엔드 모노레포 — "말 한 번으로 떠나고, 결제는 서명 한 번"

| 디렉터리 | 내용 |
| --- | --- |
| `core/` | Java · Spring Boot 메인 API: 여행 요청, 판매자 연동, 주문 진행, 결제, 서명, 원장 (포트 8080) |
| `mock-merchant/` | Java · Spring Boot Mock 판매자 A·B·C, 실패 주입·멱등 처리 (포트 9001) |
| `agent/` | Python · Google ADK · FastAPI AI 에이전트 (포트 8001) |
| `infra/` | docker compose(로컬·운영 초안), Caddy |
| `docs/` | [아키텍처](docs/architecture.md), [ADR](docs/adr) |

## 5분 온보딩

### 1. 요구사항

- Docker Desktop (compose v2)
- 직접 빌드할 때만: JDK 아무 버전 17+ (Gradle이 JDK 25를 자동으로 내려받는다), [uv](https://docs.astral.sh/uv/) 0.13+

### 2. `.env` 만들기

```bash
cp .env.example .env
```

`POSTGRES_PASSWORD`와 외부 API 테스트 키는 **노션**에서 받아 채운다. `.env`는 절대 커밋하지 않는다(`.gitignore`에 있음).

### 3. 전체 실행

```bash
docker compose -f infra/docker-compose.yml up --build
```

| 서비스 | 주소 | 헬스체크 |
| --- | --- | --- |
| core | http://localhost:8080 | `curl localhost:8080/actuator/health` |
| Swagger UI | http://localhost:8080/swagger-ui | OpenAPI: http://localhost:8080/v3/api-docs |
| agent | http://localhost:8001 | `curl localhost:8001/health` |
| mock-merchant | http://localhost:9001 | `curl localhost:9001/actuator/health` |
| postgres | localhost:5432 | `pgvector/pgvector:0.8.7-pg18` |

FE는 별도 레포에서 `pnpm dev`(5173)로 띄우고 `http://localhost:8080`을 호출한다. core는 `http://localhost:5173`에 CORS를 열어 두었다.

### 4. Mock 판매자 실패 주입

판매자는 `A`(항공), `B`(숙소), `C`(체험)이다. hold는 10분 뒤 만료된다.

```bash
# 오퍼 조회
curl localhost:9001/merchants/A/offers

# 정상 hold (201)
curl -X POST localhost:9001/merchants/A/holds \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: co_demo:A:HOLD' \
  -d '{"offerId":"A-ICN-KIX-001","quantity":1}'

# 같은 Idempotency-Key로 다시 → 첫 응답 그대로 + Idempotent-Replayed: true
curl -i -X POST localhost:9001/merchants/A/holds \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: co_demo:A:HOLD' \
  -d '{"offerId":"A-ICN-KIX-001","quantity":1}'

# 품절 (409 SOLD_OUT)
curl -i -X POST localhost:9001/merchants/A/holds \
  -H 'Content-Type: application/json' -H 'X-Mock-Fail: SOLD_OUT' \
  -d '{"offerId":"A-ICN-KIX-001","quantity":1}'

# 서버 오류 (500) / 타임아웃 (30초 멈춘 뒤 504) / 지연 (3초 뒤 정상 응답)
curl -i -X POST localhost:9001/merchants/A/holds -H 'Content-Type: application/json' -H 'X-Mock-Fail: ERROR_500' -d '{"offerId":"A-ICN-KIX-001"}'
curl -i -X POST localhost:9001/merchants/A/holds -H 'Content-Type: application/json' -H 'X-Mock-Fail: TIMEOUT'   -d '{"offerId":"A-ICN-KIX-001"}'
curl -i -X POST localhost:9001/merchants/A/holds -H 'Content-Type: application/json' -H 'X-Mock-Delay-Ms: 3000'  -d '{"offerId":"A-ICN-KIX-001"}'
```

| 엔드포인트 | 설명 |
| --- | --- |
| `GET /merchants/{id}/offers` | 오퍼 목록 |
| `POST /merchants/{id}/holds` | 재고 hold (`{"offerId","quantity"}`) |
| `POST /holds/{holdId}/release` | hold 해제(여러 번 불러도 성공) |
| `POST /holds/{holdId}/pay` | 정산 (`{"amount","currency"}`, 금액이 다르면 422, 만료면 410) |
| `POST /holds/{holdId}/confirm` | 예약 확정 → `bookingId` |
| `POST /bookings/{bookingId}/cancel` | 예약 취소 |

멱등 규칙: 2xx·4xx 응답은 저장해서 재생하고, 5xx·타임아웃은 저장하지 않아 같은 키로 재시도할 수 있다. 같은 키를 다른 요청 본문에 쓰면 422다.

## 개발

```bash
# Java: 빌드 + 테스트 + 포맷 검사(Spotless, google-java-format) + ArchUnit (Testcontainers 때문에 Docker가 켜져 있어야 한다)
./gradlew build

# 포맷 자동 수정
./gradlew spotlessApply

# 하나만 로컬 실행 (DB는 compose의 postgres 사용)
docker compose -f infra/docker-compose.yml up -d postgres mock-merchant
./gradlew :core:bootRun
```

```bash
cd agent
uv sync
uv run ruff format . && uv run ruff check . && uv run mypy && uv run pytest
uv run uvicorn trupass_agent.main:app --reload --port 8001
```

### 외부 연동 구현체 바꾸기 (Mock ↔ 실제)

`.env`에서 한 줄만 바꾼다.

```dotenv
TRUPASS_CLIENTS_MERCHANT=mock     # mock | duffel
TRUPASS_CLIENTS_PAYMENT=mock      # mock | toss
TRUPASS_CLIENTS_AUDIT=localfile   # localfile | s3
```

### 구조와 규칙

`core`는 기능(도메인)별 폴더(`trip`, `merchant`, `checkout`, `payment`, `mandate`, `ledger`) 안을 `controller / service / repository / entity / dto / exception / client`로 나눈다.
외부 API는 도메인의 `client/` 인터페이스로만 부르고, 구현체는 `global/config/ClientConfig`에서만 연결한다.
controller가 repository·client를 직접 부르거나, client 구현체를 다른 곳에서 import하면 ArchUnit 테스트가 실패해 빌드가 깨진다.
모든 API 응답은 `ApiResponse`(`isSuccess`, `code`, `message`, `result`) 형식이다. 자세한 내용은 [docs/architecture.md](docs/architecture.md)를 본다.

## 구글 로그인 테스트 (FE 없이)

`tools/google-login-test/index.html`은 core의 구글 로그인을 직접 확인하는 로컬 전용 페이지다.

1. Google Cloud Console의 OAuth 클라이언트 → 승인된 JavaScript 원본에 `http://localhost`, `http://localhost:5173`이 있어야 한다.
2. `.env`에 `GOOGLE_OAUTH_CLIENT_IDS`를 넣고 core를 띄운다(postgres 필요).
3. 테스트 페이지를 5173에서 연다. FE 개발 서버와 포트가 같으니 동시에 띄우지 않는다.

```bash
cd tools/google-login-test && python3 -m http.server 5173 --bind 127.0.0.1
```

4. Chrome에서 http://localhost:5173 → 클라이언트 ID 입력 → 구글 버튼으로 로그인 → `/users/me`, `/auth/refresh`, `/auth/logout`을 눌러 본다.
5. 처음 로그인한 계정은 회원 정보 입력 전(`profileCompleted: false`)이다. 3번 칸의 본문으로 `PUT /users/me/profile`을 보내면 가입을 마친다. 칸 규칙은 [아키텍처 문서의 "회원 정보 입력"](docs/architecture.md#회원-정보-입력-가입-2단계)에 있다.
6. `DELETE /users/me (탈퇴)`를 누르면 탈퇴한다. 그 뒤 `/users/me`는 `USER401_1`, `/auth/refresh`는 `AUTH401_3`이고, 같은 Google 계정으로 다시 로그인하면 새 회원이다.

## 웹훅 받기 (ngrok / Cloudflare Tunnel)

토스페이먼츠 같은 PG는 공인 URL로만 웹훅을 보낸다. 로컬 core(8080)를 잠깐 외부에 연다.

```bash
# ngrok
ngrok http 8080
# → https://xxxx.ngrok-free.app/webhooks/payments 를 PG 콘솔 웹훅 URL로 등록

# Cloudflare Tunnel (계정 없이 임시 URL)
cloudflared tunnel --url http://localhost:8080
# → https://xxxx.trycloudflare.com/webhooks/payments 를 등록
```

터널 URL은 실행할 때마다 바뀌므로 PG 콘솔도 다시 등록해야 한다. 테스트 키로만 쓴다.

## 배포

- **0단계:** 로컬 `infra/docker-compose.yml`
- **1단계:** EC2 한 대 + `infra/docker-compose.prod.yml` + Caddy 자동 TLS(`infra/caddy/Caddyfile`). 이미지는 main 머지 시 `ghcr.io/trupass/trupass-{core,mock-merchant,agent}`로 push된다. FE는 Vercel.
- EC2 배포 job은 `.github/workflows/docker-publish.yml`에 주석으로 자리만 있다.

## CI

- `ci.yml`: PR마다 경로 필터로 바뀐 부분만 검사한다.
  - `core/**`, `mock-merchant/**`, Gradle 공통 파일 → Gradle build + test
  - `agent/**` → `uv sync` → ruff → mypy → pytest
- `docker-publish.yml`: main 머지 시 세 서비스 이미지를 GHCR에 push한다.
