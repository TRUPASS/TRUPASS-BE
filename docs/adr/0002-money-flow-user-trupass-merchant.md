# ADR-0002: 돈의 흐름 — 사용자 → Trupass → 판매자

- 상태: accepted
- 날짜: 2026-11-10

## 맥락

패키지 하나는 여러 판매자(항공·숙소·체험)의 상품으로 이루어진다. 사용자는 서명 한 번, 결제 한 번으로 끝내야 한다.
그런데 판매자마다 결제·예약 API가 따로 있고, 중간 단계에서 실패할 수 있다.

## 결정

Trupass가 사용자에게서 한 번 받고(COLLECT), 판매자에게 나눠서 지급한다(PAY). 체크아웃은 다음 순서의 Saga로 처리한다.

```
HOLD × 판매자 수     (재고만 잡는다. 돈은 움직이지 않는다)
→ COLLECT × 1        (사용자 → Trupass. 토스페이먼츠 결제 1건)
→ PAY × 판매자 수     (Trupass → 판매자. Duffel 잔액 결제 / Mock 정산)
→ CONFIRM × 판매자 수 (예약 확정)
```

- HOLD를 먼저 해서, 재고가 없으면 사용자 돈을 건드리기 전에 끝낸다.
- 실패하면 성공한 단계를 역순으로 보상한다.
  - HOLD → `release`
  - COLLECT → 토스 **부분 취소**(이미 정산이 끝나 되돌릴 수 없는 판매자분은 제외)
  - PAY/CONFIRM → 판매자 `cancel`
- 자동 보상은 1회 재시도까지만 한다. 그래도 실패하면 체크아웃을 `NEEDS_MANUAL_REVIEW`로 멈추고 사람이 처리한다.
- Saga 상태는 DB(`checkout`, `checkout_step`)에 저장한다. 서버가 재시작돼도 `checkout/scheduler`가 멈춘 주문을 찾아 이어서 진행한다.
- 모든 외부 호출에는 `Idempotency-Key = {checkout_id}:{merchant}:{step}`을 붙인다. 재시도할 때도 같은 키를 쓴다.
  - COLLECT처럼 판매자가 없는 단계는 merchant 자리에 `trupass`를 쓴다(`IdempotencyKey.PLATFORM`).
- 금액은 `Long`(통화의 최소 단위, KRW는 원) + ISO 4217 통화 코드로만 다룬다.

## 결과

- Trupass가 결제 대행 위치에 서므로, 원장(Ledger)으로 사용자 수납액과 판매자 지급액을 대사할 수 있어야 한다. 원장 테이블 설계는 담당자가 별도 PR로 낸다.
- mock-merchant는 처음부터 실패 주입(`X-Mock-Fail`, `X-Mock-Delay-Ms`)과 멱등 처리를 실제로 지원한다. 그래야 보상 경로를 테스트할 수 있다.
- 판매자 지급 시점, 정산 주기, PG 정책(부분 취소 한도 등)은 실제 연동 단계에서 다시 검토한다.
