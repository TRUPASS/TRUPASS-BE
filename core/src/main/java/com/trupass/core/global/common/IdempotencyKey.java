package com.trupass.core.global.common;

import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;

/**
 * 상태를 바꾸는 외부 호출에 붙이는 멱등키. 형식은 {@code {주문번호}:{판매자}:{단계}}로 고정한다. 같은 단계를 재시도할 때는 반드시 같은 키를 다시 쓴다.
 *
 * <pre>{@code
 * IdempotencyKey key = IdempotencyKey.of(checkoutId, "A", "HOLD");
 * restClient.post().uri("/merchants/{id}/holds", "A")
 *     .headers(key.asHeader())
 *     .body(request)
 *     .retrieve();
 * }</pre>
 */
public record IdempotencyKey(String value) {
  public static final String HEADER = "Idempotency-Key";

  /** 사용자 → Trupass 결제(COLLECT)처럼 판매자가 아닌 Trupass 자신이 상대인 단계의 판매자 자리. */
  public static final String PLATFORM = "trupass";

  public IdempotencyKey {
    Objects.requireNonNull(value, "value");
  }

  public static IdempotencyKey of(String checkoutId, String merchantId, String step) {
    return new IdempotencyKey(
        Objects.requireNonNull(checkoutId, "checkoutId")
            + ":"
            + Objects.requireNonNull(merchantId, "merchantId")
            + ":"
            + Objects.requireNonNull(step, "step"));
  }

  public Consumer<HttpHeaders> asHeader() {
    return headers -> headers.set(HEADER, value);
  }

  @Override
  public String toString() {
    return value;
  }
}
