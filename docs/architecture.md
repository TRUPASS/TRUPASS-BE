# Trupass 백엔드 아키텍처

> "말 한 번으로 떠나고, 결제는 서명 한 번" — AI 여행 패키지 서비스

## 사용자 흐름

1. 사용자가 자연어로 여행을 요청한다.
2. AI 에이전트(`agent`)가 core의 내부 도구 API로 항공·숙소·체험 오퍼를 모아 패키지 2~3안을 만든다.
3. 사용자가 패스키로 **한 번** 서명한다. 이것이 AP2 Mandate다.
4. core가 여러 판매자에 걸친 체크아웃을 **Saga**로 처리한다.

## 서비스 구성

| 서비스 | 언어 | 포트 | 역할 |
| --- | --- | --- | --- |
| `core` | Java · Spring Boot | 8080 | 메인 API, Saga 오케스트레이터, 원장, 결제, agent용 내부 도구 API |
| `mock-merchant` | Java · Spring Boot | 9001 | 판매자 A(항공)·B(숙소)·C(체험) 흉내. 실패 주입·멱등 처리 |
| `agent` | Python · Google ADK · FastAPI | 8001 | 자연어 → 패키지 2~3안. 외부 API는 직접 부르지 않고 core만 호출 |
| `postgres` | pgvector/pgvector | 5432 | Saga 상태·원장. 나중에 TourAPI RAG 임베딩 |

```
FE(Vercel / localhost:5173) ──▶ core:8080 ──▶ mock-merchant:9001 (→ Duffel)
                                   │  ▲            Mock PG (→ 토스페이먼츠)
                                   │  └── agent:8001 (/internal/tools/*)
                                   └──▶ postgres
```

## 돈의 흐름: 사용자 → Trupass → 판매자

```
HOLD × 판매자 수     (재고만 잡는다. 돈은 움직이지 않는다)
→ COLLECT × 1        (사용자 → Trupass. 토스페이먼츠 결제 1건)
→ PAY × 판매자 수     (Trupass → 판매자. Duffel 잔액 결제 / Mock 정산)
→ CONFIRM × 판매자 수 (예약 확정)
```

- 실패하면 성공한 단계를 **역순으로 보상**한다. 판매자 hold는 release하고, 사용자 결제는 토스 부분 취소로 돌려준다.
- 자동 보상은 **1회 재시도**까지만 한다. 그래도 실패하면 `NEEDS_MANUAL_REVIEW` 상태로 멈춘다.
- Saga 상태는 메모리가 아니라 DB(`checkout`, `checkout_step`)에 저장한다.
- 모든 외부 호출에는 멱등키를 붙인다: `Idempotency-Key = {checkout_id}:{merchant}:{step}` (`IdempotencyKey.of(...)`).

자세한 결정 배경은 [ADR-0002](adr/0002-money-flow-user-trupass-merchant.md).

## 포트와 어댑터(헥사고날)

도메인 코어(Saga Orchestrator, Ledger, Mandate 검증, Package 모델)는 어떤 외부 SDK나 어댑터에도 의존하지 않는다.
외부 세계는 아래 4개 포트로만 접근하고, 어댑터는 환경 변수 한 줄로 교체한다.

| 포트 | 역할 | 지금(0~1단계) 어댑터 | 나중 어댑터 | 선택 설정 |
| --- | --- | --- | --- | --- |
| `MerchantPort` | 오퍼 조회, hold, release, pay, confirm, cancel | Mock 판매자(HTTP) | Duffel 항공·Stays | `TRUPASS_ADAPTERS_MERCHANT=mock\|duffel` |
| `PaymentPort` | 사용자 결제 승인(collect), 부분/전체 취소, 웹훅 해석 | Mock PG(인프로세스) | 토스페이먼츠 | `TRUPASS_ADAPTERS_PAYMENT=mock\|toss` |
| `AuditPort` | Mandate 원문·영수증을 수정 불가능하게 보관 | 로컬 JSON 파일(append-only) | S3 Object Lock | `TRUPASS_ADAPTERS_AUDIT=localfile\|s3` |
| `SagaStatePort` | 체크아웃 진행 상태 저장·재개 | Postgres | Temporal | (선택지 없음) |

### core 패키지 구조

```
com.trupass.core
├── domain/          순수 도메인(Java record). JDK만 사용
│   ├── common/      Money(Long + 통화 코드)
│   ├── checkout/    CheckoutId, MerchantId, IdempotencyKey
│   ├── saga/        SagaState, SagaStepType, CheckoutStatus
│   ├── merchant/    Hold, Booking, MerchantFailureException …
│   ├── payment/     CollectRequest, Payment, PaymentCancelRequest …
│   ├── mandate/     MandateRecord, ReceiptRecord, AuditRef
│   ├── ledger/      (비어 있음 — 원장 담당자)
│   └── pkg/         Offer, OfferSearchCriteria
├── application/
│   └── port/        MerchantPort, PaymentPort, AuditPort, SagaStatePort
├── adapter/
│   ├── inbound/web/ REST 컨트롤러, 웹훅 수신, /internal/tools (agent용)
│   └── outbound/    merchant/{mock,duffel}, payment/{mockpg,toss}, audit/{localfile,s3}, persistence/jpa
└── config/          어댑터 선택, RestClient 공통 설정, CORS, OpenAPI
```

### 경계 강제(ArchUnit)

`core/src/test/java/com/trupass/core/ArchitectureTest.java`가 `./gradlew build`에서 다음을 검사한다.

- `domain`은 `domain`·`java` 패키지에만 의존한다(Spring·JPA·Jackson·외부 SDK 금지).
- `domain`은 `application`·`adapter`·`config`를 모른다.
- `application`은 `adapter`·`config`를 모른다.
- `adapter.inbound`는 `adapter.outbound`를 직접 부르지 않는다.

`in`은 Java 예약어라 패키지 이름으로 쓸 수 없어서 `inbound`/`outbound`로 이름을 붙였다.

## 공통 HTTP 클라이언트

외부 호출은 Spring `RestClient`로 하고, 어댑터는 주입받은 `RestClient.Builder`에 `clone().baseUrl(...)`만 붙인다.

- 연결·읽기 타임아웃 기본값: `trupass.http.connect-timeout`(2s), `trupass.http.read-timeout`(5s)
- `CheckoutContextInterceptor`: MDC `checkout_id`를 `X-Checkout-Id` 헤더로 전파하고 호출 결과를 구조화 로그로 남긴다.
- `Idempotency-Key`: `restClient.post()...headers(IdempotencyKeys.header(key))`

로그는 Spring Boot 기본 structured logging(ECS JSON)으로 남기고, MDC `checkout_id`가 필드로 함께 찍힌다.

## 배포 단계

| 단계 | 형태 | 이번 세팅 범위 |
| --- | --- | --- |
| 0 | 로컬 `docker compose` (`infra/docker-compose.yml`) | ✅ |
| 1 | EC2 한 대 + docker compose + Caddy 자동 TLS (`infra/docker-compose.prod.yml`). FE는 Vercel | ✅ 초안 |
| 2~3 | ECS/EKS | ❌ |
