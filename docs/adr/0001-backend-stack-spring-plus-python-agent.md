# ADR-0001: 백엔드 스택 — Java/Spring Boot core + Python agent

- 상태: accepted
- 날짜: 2026-11-10

## 맥락

Trupass 백엔드는 성격이 다른 두 가지 일을 한다.

1. 돈이 오가는 체크아웃: 여러 판매자에 걸친 Saga, 원장, 결제 취소, 멱등성, 감사 로그. 정확성과 트랜잭션 처리가 가장 중요하다.
2. AI 에이전트: 자연어 요청을 받아 오퍼를 조합한다. Google ADK·Gemini·AP2 SDK가 Python 생태계에 먼저 나온다.

## 결정

- **core**와 **mock-merchant**는 Java + Spring Boot로 만든다. Kotlin은 쓰지 않는다(빌드 스크립트도 Groovy DSL).
  - Gradle(Groovy DSL) 멀티모듈, 버전 카탈로그(`gradle/libs.versions.toml`)
  - JDK 25(LTS), Spring Boot 4.1.2. 도메인 타입은 Java record로 만든다.
  - Spring Web MVC, Validation, Actuator, Data JPA + PostgreSQL, Flyway, springdoc-openapi
  - 테스트: JUnit 5, Testcontainers(PostgreSQL), ArchUnit. 포맷: Spotless + google-java-format 2.0.0
- **agent**는 Python으로 만든다.
  - uv, Python 3.12 이상(로컬·이미지는 3.14)
  - Google ADK(`google-adk`), Gemini(`google-genai`), FastAPI, pydantic v2, httpx
  - AP2 Python SDK는 `google-agentic-commerce/AP2` GitHub 저장소를 커밋으로 고정해 설치한다. PyPI `ap2`는 제3자 미러라 쓰지 않는다.
  - 품질 도구: ruff, mypy(strict), pytest
- agent는 외부 API(Duffel, TourAPI 등)를 **직접 부르지 않는다.** core의 `/internal/tools/*`만 호출한다.
  - 키 관리, 레이트 리밋, 감사 로그, 어댑터 교체가 core 한 곳에 모인다.
  - 금액은 항상 오퍼 ID와 함께 다닌다. LLM이 금액을 만들거나 고치지 않는다.

## 결과

- 두 언어를 쓰므로 CI는 경로 필터로 바뀐 부분만 검사한다.
- core ↔ agent 계약은 core의 OpenAPI(`/v3/api-docs`)와 agent의 Pydantic 스키마(camelCase JSON)로 맞춘다.
- 버전 고정 중 생긴 예외(2026-11-10):
  - AP2 SDK(커밋 `e1ea56d`)가 `pydantic==2.12.5`, `pytest==9.0.2`를 정확히 고정하고 있다. 그래서 최신(2.14.3 / 9.2.0) 대신 이 버전을 따른다.
  - `google-adk==2.14.0`이 `google-genai<3`을 요구한다. 그래서 `google-genai`는 3.0.0이 아니라 2.x 최신인 2.34.0으로 고정한다.
