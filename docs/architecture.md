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
│   ├── ledger/      원장: 돈이 어디서 어디로 얼마 움직였는지 기록
│   ├── user/        사용자 (Google 계정으로 가입)
│   └── auth/        Google 로그인, access/refresh token 발급
└── global/
    ├── apiPayload/  공통 응답(ApiResponse), 에러 코드, 전역 예외 처리
    ├── config/      외부 연동 구현체 선택, RestClient·CORS·Swagger 설정
    ├── common/      여러 도메인이 같이 쓰는 값 (Money, IdempotencyKey)
    ├── security/    Spring Security(Bearer JWT), access token 발급·검증, @LoginUserId
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
| `event/` | 다른 도메인에 알리는 사건 (`UserWithdrawnEvent` 등). 받는 쪽은 자기 service에 `@EventListener`를 둔다. 다른 도메인을 직접 부르면 서로 기대게 될 때 쓴다 |

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
| `user` | `User` 엔티티(Google 계정 + 닉네임·출생 연도·성별·여행 관심사·약관 동의), `AgreementHistory`(약관 동의·철회 이력), `UserService`(추천용 `getTravelPreference` 포함), `UserController`(`GET /users/me`, `PUT /users/me/profile`, `DELETE /users/me`), `UserWithdrawnEvent` | |
| `auth` | `AuthController`(`/auth/google`, `/auth/refresh`, `/auth/logout`), `AuthService`, `RefreshTokenService`, `GoogleAuthClient` → `GoogleIdTokenClient` | |

아직 구현하지 않은 client 메서드는 `UnsupportedOperationException`을 던지고, 전역 예외 처리가 이를 `501 COMMON501`로 바꾼다.
테이블은 `V1__baseline.sql`이 비어 있고, 담당자가 `V2__*.sql`부터 추가한다(`ddl-auto: validate`).

### 공통 응답과 에러

모든 API는 `ApiResponse`로 감싼다.

```json
{ "isSuccess": true,  "code": "COMMON200",    "message": "성공했습니다.", "result": { ... } }
{ "isSuccess": false, "code": "MERCHANT409_1", "message": "품절된 상품입니다." }
```

- 성공: `return ApiResponse.onSuccess(result);`
- 실패: `throw new GeneralException(MerchantErrorCode.SOLD_OUT);` → `GlobalExceptionHandler`가 상태 코드와 응답을 만든다.
- 에러 코드는 `BaseErrorCode`를 구현한 도메인별 enum(`XxxErrorCode`)에 둔다. 코드 형식은 `{도메인}{HTTP 상태}_{번호}`(예: `PAYMENT422_1`).
- 검증 실패는 `COMMON400_1`이고, `result`에 `{필드: 메시지}`가 담긴다. 404·405 같은 Spring 기본 에러도 같은 형식으로 내려간다.

### 로그인 (Google)

```
FE ── Google Identity Services ──▶ ID 토큰(credential)
FE ── POST /auth/google {idToken} ──▶ core: Google 공개키로 서명·aud·iss·만료 검증
                                         → 사용자 찾기/가입 → access token(JWT 30분) + refresh token(14일)
                                         + user(profileCompleted 포함) + newUser
   user.profileCompleted = false → FE 회원 정보 입력(/signup)
FE ── PUT /users/me/profile {nickname, birthYear, gender, interests, agreements} ──▶ 저장, profileCompleted = true
FE ── Authorization: Bearer {accessToken} ──▶ 인증이 필요한 API
FE ── POST /auth/refresh {refreshToken} ──▶ 새 토큰 쌍 (refresh token은 1회용, 재사용되면 그 사용자 것 전부 폐기)
FE ── POST /auth/logout {refreshToken} ──▶ refresh token 폐기
```

- 열린 경로: `/auth/**`, `/internal/**`(agent 전용, 운영은 Caddy가 차단), `/webhooks/**`, health, Swagger. 나머지는 모두 로그인이 필요하다.
- controller에서 로그인 사용자는 `@LoginUserId Long userId`로 받는다. OpenAPI 스펙에는 나오지 않는다(`SwaggerConfig`).
- 토큰이 없으면 `COMMON401`, 틀리거나 만료되면 `COMMON401_1`.
- 설정: `GOOGLE_OAUTH_CLIENT_IDS`(Google OAuth 클라이언트 ID), `TRUPASS_JWT_SECRET`(32바이트 이상, prod 필수), 운영은 `TRUPASS_CORS_ALLOWED_ORIGINS`(FE 주소).

### 회원 정보 입력 (가입 2단계)

Google 로그인 때 계정이 먼저 생기고(`newUser = true`), FE 회원 정보 입력 화면에서 `PUT /users/me/profile`을 보내면 가입을 마친다.
회원 정보 입력 화면으로 보낼지는 `newUser`가 아니라 **`user.profileCompleted`** 로 정한다. `newUser`는 처음 한 번만 true라서, 입력을 마치지 않고 나갔다가 다시 로그인한 사람을 놓친다.

| 칸 (FE 화면) | 필드 | 규칙 |
| --- | --- | --- |
| 이메일 | — | Google 계정의 것. 받지 않는다 |
| 닉네임 | `nickname` | 필수, 앞뒤 공백을 지우고 2~12자, 제어 문자 불가 |
| 출생 연도 | `birthYear` | 필수, 1900 ~ 올해 (올해보다 뒤면 `USER400_1`) |
| 성별 (선택) | `gender` | `FEMALE` · `MALE`, "선택 안 함"은 `null`(또는 생략) |
| 여행 관심사 (최대 3개) | `interests` | 0~3개. `FOOD` 미식·맛집, `SIGHTSEEING` 관광·명소, `SHOPPING` 쇼핑, `RELAXATION` 휴양·온천, `CULTURE` 문화·예술, `ARCHITECTURE` 건축·도시 산책, `NATURE` 자연·야외 활동, `THEME_PARK` 테마파크·체험 |
| 약관 | `agreements` | `termsOfService`(필수) · `privacyCollection`(필수)은 true, `marketing`(선택, 여행 혜택·알림 수신) |

- 칸이 틀리면 `COMMON400_1`이고 `result`에 `{칸: 메시지}`가 담긴다(예: `{"agreements.termsOfService": "필수 약관에 동의해야 합니다."}`).
- 같은 API로 설정에서 고친다(전체를 다시 보낸다). 같은 값을 다시 보내도 결과가 같고, `profileCompleted`는 처음 저장한 뒤로 계속 true다.
- 약관은 `user_agreements`에 처음 동의한 시각과 함께 남긴다. 선택 약관(`marketing`)을 false로 보내면 동의를 거둔 것으로 보고 지운다.
- `GET /users/me`와 로그인 응답의 `user`는 같은 형식(`Me`)이고, 위 값과 `agreements`(지금 유지 중인 마케팅 수신 동의 시각 `marketingAgreedAt` 포함), `profileCompleted`를 함께 준다.

### 약관 동의 이력

- `user_agreements`는 지금 상태, `user_agreement_histories`는 바뀐 기록이다. 동의하거나 거둘 때마다(회원 정보 입력·수정, 탈퇴) 한 줄씩 쌓고 지우거나 고치지 않는다. 같은 값을 다시 보내면 쌓지 않는다.
- 마케팅(여행 혜택·알림) 수신을 언제 동의하고 언제 거뒀는지 이것으로 증명한다. V4 이전에 받은 동의는 마이그레이션이 첫 기록으로 옮겼다.

### 회원 탈퇴 (`DELETE /users/me`)

- 행은 지우지 않는다(주문·서명 기록이 가리킬 수 있다). 개인정보(이메일·이름·사진·닉네임·출생 연도·성별·관심사)를 지우고 `withdrawn_at`을 채운다. 약관 동의는 모두 거둔 것으로 이력에 남긴다.
- `google_sub`를 `withdrawn:{id}`로 바꿔 Google 계정과의 연결을 끊는다. 같은 Google 계정으로 다시 로그인하면 새 회원으로 가입된다.
- `UserWithdrawnEvent`를 같은 트랜잭션에서 발행하고, auth(`RefreshTokenService`)가 받아 그 회원의 refresh token을 모두 폐기한다.
- 이미 발급한 access token은 만료(30분)까지 남지만, 탈퇴한 회원으로 오는 요청은 `USER401_1`(401)로 거절한다. FE는 401을 받으면 재발급을 시도하고, refresh token이 폐기돼 있어 로그아웃된다.
- FE에는 아직 탈퇴 화면이 없다.

### 추천용 회원 성향

- `UserService.getTravelPreference(userId)`는 연령대(10살 단위, 연 나이 기준)와 여행 관심사만 준다. 출생 연도는 넘기지 않는다. 회원 정보 입력 전이면 비어 있다.
- 여행 요청(trip) API가 생기면 `TripService`가 이 값을 agent 요청에 싣는다. agent 요청 형식(`AgentClientReqDTO.Compose`)은 그때 함께 바꾼다.

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
