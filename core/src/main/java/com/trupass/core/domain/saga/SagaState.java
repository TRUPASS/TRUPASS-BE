package com.trupass.core.domain.saga;

import com.trupass.core.domain.checkout.CheckoutId;
import java.util.List;

/**
 * {@code checkout} + {@code checkout_step} 테이블에 영속되는 Saga 스냅샷.
 *
 * @param version 낙관적 락에 쓴다.
 */
public record SagaState(
    CheckoutId checkoutId, CheckoutStatus status, List<SagaStepState> steps, long version) {
  public SagaState {
    steps = List.copyOf(steps);
  }
}
