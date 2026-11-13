package com.trupass.core.domain.checkout.entity.enums;

/**
 * 주문 진행 단계. 순서는 HOLD × 판매자 수 → COLLECT × 1 → PAY × 판매자 수 → CONFIRM × 판매자 수. 실패하면 성공한 단계를 역순으로
 * 되돌린다(hold 해제, 결제 부분 취소, 예약 취소). 멱등키의 {@code {단계}} 자리에 이 이름을 쓴다.
 */
public enum CheckoutStepType {
  HOLD,
  COLLECT,
  PAY,
  CONFIRM
}
