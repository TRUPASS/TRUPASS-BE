# ADR-0004: core 구조 — 헥사고날에서 도메인별 레이어 구조로

- 상태: accepted
- 날짜: 2026-11-13

## 맥락

초기 세팅은 core를 포트와 어댑터(헥사고날)로 나눴다(`domain` / `application/port` / `adapter/{inbound,outbound}` / `config`).
경계는 분명했지만, 기능 하나를 만들 때 네 군데 패키지를 오가야 했고 팀에 익숙한 Spring 레이어 구조(controller / service / repository)와도 달랐다.

## 결정

- **기능(도메인)별로 폴더를 나누고**, 그 안을 `controller / service / repository / entity / dto / exception`으로 나눈다.
  - 도메인: `trip`, `merchant`, `checkout`, `payment`, `mandate`, `ledger`
  - 공통: `global/{apiPayload, config, common, web}`
- 외부 API는 도메인 안의 **`client/`에 인터페이스 + 구현체**로 둔다. 헥사고날의 "포트 → 어댑터 교체"는 이 방식으로 유지한다.
  - 구현체 선택은 `trupass.clients.*` 설정 한 줄(`TRUPASS_CLIENTS_*` 환경 변수)이고, 연결은 `global/config/ClientConfig`에서만 한다.
- 모든 API 응답은 `ApiResponse`(`isSuccess`, `code`, `message`, `result`)로 감싸고, 에러는 도메인별 `XxxErrorCode` + `GeneralException`으로 던진다.
- 규칙 1~3(controller → service만, 외부 API는 client로만, client 구현체는 config에서만 연결)은 ArchUnit으로 빌드에서 검사한다.

## 결과

- 기능 하나가 한 폴더 안에 모여 찾기 쉽다. 새 팀원이 익숙한 구조다.
- 도메인 클래스가 Spring·JPA에 의존하는 것을 막지 않는다. 대신 외부 연동 경계(`client/`)만은 ArchUnit으로 지킨다.
- 설정 키가 `trupass.adapters.*` → `trupass.clients.*`로 바뀌었다. 로컬 `.env`의 `TRUPASS_ADAPTERS_*`도 `TRUPASS_CLIENTS_*`로 바꿔야 한다.
- agent는 core 응답의 `result`를 꺼내 쓴다(`CoreApiResponse`). 실패 응답은 `CoreApiError`(core 에러 코드 포함)로 받는다.
