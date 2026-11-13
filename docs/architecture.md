# Trupass 백엔드 아키텍처

> "말 한 번으로 떠나고, 결제는 서명 한 번" — AI 여행 패키지 서비스

## 요약

- **기능(도메인)별로 폴더를 나누고**, 그 안을 `controller / service / repository / entity / dto / exception`으로 나눈다.
- **외부 API는 `client/`에 인터페이스 + 구현체로** 둔다. Mock과 실제 서비스(Duffel, 토스)를 설정 한 줄로 바꿀 수 있다.
- **Java 백엔드(`core`)와 AI 에이전트(`agent`)는 한 레포에** 있고, 서로 HTTP로만 통신한다.

구조를 이렇게 정한 배경은 [ADR-0004](adr/0004-domain-layered-structure.md).

## 사용자 흐름

1. 사용자가 자연어로 여행을 요청한다.
2. core(`trip`)가 AI 에이전트(`agent`)에게 패키지 조합을 요청한다. agent는 core의 내부 도구 API로 항공·숙소·체험 상품을 모아 패키지 2~3안을 만든다.
3. 사용자가 패스키로 **한 번** 서명한다. 이것이 AP2 Mandate다.
4. core(`checkout`)가 여러 판매자에 걸친 주문을 HOLD → COLLECT → PAY → CONFIRM 순서로 진행하고, 실패하면 되돌린다.

## 1. 레포 전체

```
TRUPASS-BE/
├── core/            메인 백엔드 (Java · Spring Boot, :8080)
├── agent/           AI 에이전트 (Python · Google ADK, :8001)
├── mock-merchant/   가짜 판매자 서버 (항공·숙소·체험, :9001)
├── infra/           docker-compose, 배포 설정
├── docs/            아키텍처 문서, 결정 기록(ADR)
└── .github/         CI, 이슈·PR 템플릿
```

`docker compose -f infra/docker-compose.yml up` 한 줄로 core, agent, mock-merchant, DB가 같이 뜬다.

```
FE(Vercel / localhost:5173) ──▶ core:8080 ──▶ mock-merchant:9001 (→ Duffel)
                                  │  ▲            Mock PG (→ 토스페이먼츠)
                                  │  │
                          /compose│  │/internal/tools/*
                                  ▼  │
                               agent:8001
                                  
                           core ──▶ postgres:5432
```

## 2. 사용 기술과 버전

### core · mock-merchant (Java)

| 기술 | 버전 | 비고 |
| --- | --- | --- |
| Java | 25 (LTS) | Gradle이 로컬에 없으면 자동으로 내려받는다 |
| Spring Boot | 4.1.2 | Web MVC, Validation, Actuator, Data JPA, Flyway |
| Gradle | 9.9.0 | Wrapper(`./gradlew`)로 실행 |
| PostgreSQL | 18 (`pgvector/pgvector:0.8.7-pg18`) | 로컬 compose와 테스트가 같은 이미지를 쓴다 |
| springdoc-openapi | 3.2.0 | Swagger UI, FE 타입 생성용 OpenAPI |
| 테스트 | JUnit 5, Testcontainers, ArchUnit 1.5.1 |  |
| 포맷 | Spotless 8.11.0 + google-java-format 2.0.0 | 2칸 들여쓰기, 100자 |

### agent (Python)

| 기술 | 버전 | 비고 |
| --- | --- | --- |
| Python | 3.14 (최소 3.12) |  |
| uv | 0.13.7 | 패키지 관리, `uv.lock`으로 버전 고정 |
| Google ADK | 2.14.0 | 에이전트 프레임워크 |
| google-genai | 2.34.0 | Gemini API (모델 이름은 환경 변수 `GEMINI_MODEL`) |
| FastAPI | 0.162.2 |  |
| pydantic | 2.12.5 | AP2 SDK가 요구하는 버전에 맞춤 |
| AP2 SDK | GitHub 커밋 고정 | PyPI의 `ap2`는 공식 패키지가 아니라 쓰지 않는다 |
| 품질 도구 | ruff 0.17.4, mypy 2.4.1 (strict), pytest 9.0.2 |  |

### 버전을 적어 두는 곳

- Java: `gradle/libs.versions.toml` (Spring Boot가 관리하는 라이브러리는 BOM이 맞춰 준다)
- Python: `agent/pyproject.toml` + `agent/uv.lock`
- Docker 이미지: `infra/docker-compose.yml`, 각 `Dockerfile`

## 3. core 구조

```
core/src/main/java/com/trupass/core/
├── domain/
│   ├── trip/        여행 요청 → AI에게 패키지 조합 요청 → 패키지 후보 저장
│   ├── merchant/    판매자 연동: 상품 검색, 자리 잡기·대금 지급·예약 확정·취소
│   ├── checkout/    주문 진행: HOLD → COLLECT → PAY → CONFIRM, 실패 시 되돌리기
│   ├── payment/     사용자 결제: 승인·부분 취소·웹훅
│   ├── mandate/     사용자 서명 검증, 서명 원본·영수증 보관
│   └── ledger/      원장: 돈이 어디서 어디로 얼마 움직였는지 기록
└── global/
    ├── apiPayload/  공통 응답(ApiResponse), 에러 코드, 전역 예외 처리
    ├── config/      외부 연동 구현체 선택, RestClient·CORS·Swagger 설정
    ├── common/      여러 도메인이 같이 쓰는 값 (Money, IdempotencyKey)
    └── web/         공통 필터 (요청마다 주문 번호를 로그에 붙임)
```

### 도메인 안의 폴더

필요한 것만 만든다.

| 폴더 | 들어가는 것 |
| --- | --- |
| `controller/` | API 엔드포인트. 요청 검증 → service 호출 → 응답 |
| `service/` | 비즈니스 로직, 트랜잭션 |
| `repository/` | DB 조회·저장 |
| `entity/` | DB 테이블 클래스, `enums/`. 상태 변경 메서드도 여기에 둔다 (`markAsPaid()` 등) |
| `dto/` | 요청·응답 형식 (`XxxReqDTO`, `XxxResDTO` 안에 record) |
| `exception/` | 도메인별 에러 코드 (`XxxErrorCode`) |
| `client/` | 외부 API 인터페이스 + 구현체 (아래 "왜 이 구조인가") |
| `scheduler/` | 재시도·재개 같은 주기 작업 |

예시 (`payment`, 테이블이 생기면 `entity/`·`repository/`가 추가된다):

```
payment/
├── controller/   PaymentWebhookController
├── service/      PaymentService
├── exception/    PaymentErrorCode
└── client/
    ├── PaymentClient.java              ← 인터페이스 (service는 이것만 안다)
    ├── dto/PaymentClientReqDTO.java    ← client 요청·응답 형식
    ├── mock/MockPgClient.java          ← 개발·테스트용 가짜 결제
    └── toss/TossPaymentClient.java
```

### 지금 들어 있는 것

| 도메인 | 들어 있는 것 | 아직 없는 것 (담당자가 추가) |
| --- | --- | --- |
| `trip` | `TripService`, `AgentClient` → `HttpAgentClient`(agent `/compose`), `TripErrorCode` | 여행 요청·패키지 후보 엔티티, API |
| `merchant` | `InternalToolController`(`/internal/tools/search-offers`), `MerchantService`, `MerchantClient` → `MockMerchantClient` / `DuffelMerchantClient`, `MerchantErrorCode` | client 구현 |
| `checkout` | `entity/enums`(`CheckoutStatus`, `CheckoutStepType`, `CheckoutStepStatus`), `CheckoutErrorCode` | 주문·단계 엔티티, 진행 service, 재개 scheduler |
| `payment` | `PaymentWebhookController`(`/webhooks/payments`), `PaymentService`, `PaymentClient` → `MockPgClient` / `TossPaymentClient`, `PaymentErrorCode` | Payment 엔티티, client 구현 |
| `mandate` | `MandateService`, `AuditClient` → `LocalFileAuditClient` / `S3AuditClient`, `MandateErrorCode` | 서명 검증([ADR-0003](adr/0003-webauthn-library-candidates.md)), client 구현 |
| `ledger` | (비어 있음) | 원장 엔티티·service |

아직 구현하지 않은 client 메서드는 `UnsupportedOperationException`을 던지고, 전역 예외 처리가 이를 `501 COMMON501`로 바꾼다.
테이블은 `V1__baseline.sql`이 비어 있고, 담당자가 `V2__*.sql`부터 추가한다(`ddl-auto: validate`).

### 공통 응답과 에러

모든 API는 `ApiResponse`로 감싼다.

```json
{ "isSuccess": true,  "code": "COMMON200",    "message": "성공했습니다.", "result": { ... } }
{ "isSuccess": false, "code": "MERCHANT4091", "message": "품절된 상품입니다." }
```

- 성공: `return ApiResponse.onSuccess(result);`
- 실패: `throw new GeneralException(MerchantErrorCode.SOLD_OUT);` → `GlobalExceptionHandler`가 상태 코드와 응답을 만든다.
- 에러 코드는 `BaseErrorCode`를 구현한 도메인별 enum(`XxxErrorCode`)에 둔다. 코드 형식은 `{도메인}{HTTP 상태}{번호}`(예: `PAYMENT4221`).
- 검증 실패는 `COMMON4001`이고, `result`에 `{필드: 메시지}`가 담긴다. 404·405 같은 Spring 기본 에러도 같은 형식으로 내려간다.

## 4. 왜 이 구조인가

| Trupass의 특징 | 구조에서 해결하는 방법 |
| --- | --- |
| 외부 상대가 바뀐다 (Mock 판매자 → Duffel, Mock PG → 토스, 로컬 파일 → S3) | `client/` 인터페이스. 설정만 바꾸면 구현체가 바뀌고 service 코드는 그대로 |
| 품절·타임아웃 같은 실패 경로를 테스트해야 한다 | 일부러 실패하는 Mock 구현체를 끼워서, 실제 API 없이 되돌리기 로직을 검증 |
| 사용자 결제와 판매자 정산은 상대도, 실패 방식도 다르다 | `payment`(사용자 ↔ Trupass)와 `merchant`(Trupass ↔ 판매자)로 분리 |
| AI 응답은 느리고 불안정하다 | `trip`에서만 AI를 부르고, 결제 흐름(`checkout`)과 분리 |

### client 설정

```yaml
trupass:
  clients:
    merchant: mock     # mock | duffel
    payment: mock      # mock | toss
    audit: localfile   # localfile | s3
```

환경 변수로는 `TRUPASS_CLIENTS_MERCHANT=duffel`처럼 바꾼다. 값은 enum이라 오타가 있으면 애플리케이션이 뜨지 않는다.
구현체 연결은 `global/config/ClientConfig` 한 곳에서만 한다.

## 5. agent 구조

```
agent/
├── pyproject.toml         의존성 (uv로 관리)
├── src/trupass_agent/
│   ├── main.py            FastAPI 입구. core가 "패키지 만들어 줘"라고 부르는 곳 (/compose)
│   ├── agent.py           ADK 에이전트 정의, 프롬프트
│   ├── tools/             에이전트가 쓰는 도구. core의 /internal/tools API를 호출
│   ├── schemas.py         입출력 형식 (패키지, 상품 참조)
│   └── config.py          환경 변수 (Gemini 모델, core 주소)
└── tests/
```

### core와 agent가 주고받는 것

```
core (trip)     ──▶  agent /compose            "이 요청으로 패키지 2~3안 만들어 줘"
agent (tools)   ──▶  core /internal/tools/*    "이 조건으로 상품 검색해 줘"
```

- agent는 **외부 API(Duffel, 토스 등)를 직접 부르지 않는다.** 항상 core를 통한다.
- agent는 **금액을 만들거나 바꾸지 않는다.** 상품 ID를 조합하고, 금액은 core가 확정한다.
- `/internal/**`은 agent 전용이다. 운영 환경에서는 외부에 열지 않는다(`infra/caddy/Caddyfile`).
- core 응답은 `ApiResponse` 형식이라 agent의 `CoreClient`가 `result`를 꺼내 쓰고, 실패면 `CoreApiError`(core 에러 코드 포함)를 던진다.
- JSON 필드는 양쪽 모두 camelCase다.

## 6. 규칙

1. controller는 service만 부른다. repository와 client를 직접 부르지 않는다.
2. 외부 API는 `client/` 인터페이스로만 부른다. service에서 `RestClient`를 직접 쓰지 않는다.
3. client 구현체(`TossPaymentClient` 등)는 `global/config`에서만 연결한다. 다른 곳에서 직접 import하지 않는다.
4. 다른 도메인의 기능이 필요하면 그 도메인의 service를 부른다. 남의 repository는 쓰지 않는다.
5. 상태를 바꾸는 외부 호출에는 멱등키(`{주문번호}:{판매자}:{단계}`)를 붙인다. 재시도할 때도 같은 키를 쓴다.
6. DB 트랜잭션 안에서 외부 API를 부르지 않는다. 순서는 "상태 저장 → 외부 호출 → 결과 저장"이다.

1~3번은 ArchUnit 테스트(`core/src/test/java/com/trupass/core/ArchitectureTest.java`)로 빌드할 때 자동 검사한다.

## 돈의 흐름: 사용자 → Trupass → 판매자

```
HOLD × 판매자 수     (재고만 잡는다. 돈은 움직이지 않는다)
→ COLLECT × 1        (사용자 → Trupass. 토스페이먼츠 결제 1건)
→ PAY × 판매자 수     (Trupass → 판매자. Duffel 잔액 결제 / Mock 정산)
→ CONFIRM × 판매자 수 (예약 확정)
```

- 실패하면 성공한 단계를 **역순으로 되돌린다**. 판매자 hold는 release하고, 사용자 결제는 토스 부분 취소로 돌려준다.
- 자동 되돌리기는 **1회 재시도**까지만 한다. 그래도 실패하면 `NEEDS_MANUAL_REVIEW` 상태로 멈춘다.
- 주문 진행 상태는 메모리가 아니라 DB(`checkout`, `checkout_step`)에 저장한다.
- 멱등키는 `IdempotencyKey.of(checkoutId, merchantId, step)`으로 만든다. COLLECT처럼 판매자가 없는 단계는 `IdempotencyKey.PLATFORM`(`trupass`)을 쓴다.

자세한 결정 배경은 [ADR-0002](adr/0002-money-flow-user-trupass-merchant.md).

## 공통 HTTP 클라이언트와 로그

외부 호출은 Spring `RestClient`로 하고, client 구현체는 `ClientConfig`에서 `RestClient.Builder`에 `clone().baseUrl(...)`만 붙여 받는다.

- 연결·읽기 타임아웃 기본값: `trupass.http.connect-timeout`(2s), `trupass.http.read-timeout`(5s)
- `global/web/CheckoutContextInterceptor`: MDC `checkout_id`를 `X-Checkout-Id` 헤더로 전파하고 호출 결과를 구조화 로그로 남긴다.
- `global/web/CheckoutIdMdcFilter`: 들어오는 요청의 `X-Checkout-Id`를 MDC에 넣는다.
- `Idempotency-Key`: `restClient.post()...headers(key.asHeader())`

로그는 Spring Boot 기본 structured logging(ECS JSON)으로 남기고, MDC `checkout_id`가 필드로 함께 찍힌다.

## 배포 단계

| 단계 | 형태 | 이번 세팅 범위 |
| --- | --- | --- |
| 0 | 로컬 `docker compose` (`infra/docker-compose.yml`) | ✅ |
| 1 | EC2 한 대 + docker compose + Caddy 자동 TLS (`infra/docker-compose.prod.yml`). FE는 Vercel | ✅ 초안 |
| 2~3 | ECS/EKS | ❌ |
