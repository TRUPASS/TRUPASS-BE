# ADR-0003: WebAuthn(패스키) 검증 라이브러리 후보

- 상태: **proposed**
- 날짜: 2026-11-10

## 맥락

사용자는 패스키로 **한 번** 서명해 AP2 Mandate를 승인한다. core는 그 서명(WebAuthn assertion)을 검증하고, 원문을 `AuditPort`로 보관해야 한다.
아직 라이브러리를 정하지 않았다. 그래서 이번 초기 세팅에서는 **의존성을 추가하지 않았다.**

## 후보

| 후보 | 장점 | 확인할 점 |
| --- | --- | --- |
| Spring Security 패스키 지원 (`spring-security-webauthn`) | Spring Boot와 통합. 등록·로그인 플로우와 필터가 기본 제공됨 | 로그인 용도 중심이라 "Mandate 페이로드에 대한 서명 검증"에 맞출 수 있는지. 내부적으로 webauthn4j 사용 |
| webauthn4j | 저수준 검증 API. challenge에 임의 데이터(Mandate 해시)를 넣고 검증하기 쉬움. Spring Security가 내부에서 사용 | 등록·저장소·challenge 관리는 직접 구현해야 함 |
| Yubico `java-webauthn-server` | 성숙한 레퍼런스 구현. 문서와 테스트가 충실함 | `CredentialRepository` 구현 필요. Kotlin/Spring과 함께 쓴 사례 확인 |

## 결정에 필요한 기준

1. Mandate 해시를 challenge로 묶어 "이 금액·이 오퍼 묶음에 서명했다"를 증명할 수 있는가
2. 검증 로직을 `domain`(순수)과 `adapter`(라이브러리 의존)로 깔끔하게 나눌 수 있는가
3. 라이선스, 유지보수 활성도, JDK 25·Spring Boot 4 호환성

## 다음 단계

- 담당자가 세 후보로 Mandate 서명 검증 PoC를 만들어 비교한 뒤, 이 ADR을 `accepted`로 바꾸고 의존성을 추가한다.
