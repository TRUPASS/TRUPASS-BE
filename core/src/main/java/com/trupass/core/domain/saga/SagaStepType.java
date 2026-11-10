package com.trupass.core.domain.saga;

/**
 * Saga 정방향 단계. 순서는 HOLD × 판매자 수 → COLLECT × 1 → PAY × 판매자 수 → CONFIRM × 판매자 수. 보상 단계(RELEASE,
 * REFUND 등)는 각 정방향 단계의 역연산이며, 오케스트레이터 담당자가 확정한다.
 */
public enum SagaStepType {
  HOLD,
  COLLECT,
  PAY,
  CONFIRM
}
