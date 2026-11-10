package com.trupass.core.domain.checkout;

import com.trupass.core.domain.saga.SagaStepType;
import java.util.Objects;

/**
 * 모든 외부 호출에 붙이는 멱등키. 형식은 {@code {checkout_id}:{merchant}:{step}}으로 고정한다. 같은 단계를 재시도할 때는 반드시 같은 키를
 * 다시 써야 한다.
 */
public record IdempotencyKey(String value) {
  public IdempotencyKey {
    Objects.requireNonNull(value, "value");
  }

  public static IdempotencyKey of(CheckoutId checkoutId, MerchantId merchantId, SagaStepType step) {
    return new IdempotencyKey(checkoutId.value() + ":" + merchantId.value() + ":" + step.name());
  }

  @Override
  public String toString() {
    return value;
  }
}
