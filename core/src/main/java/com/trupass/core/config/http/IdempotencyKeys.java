package com.trupass.core.config.http;

import com.trupass.core.domain.checkout.IdempotencyKey;
import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;

/**
 * 요청에 {@code Idempotency-Key} 헤더를 붙인다.
 *
 * <pre>{@code
 * restClient.post().uri("/merchants/{id}/holds", id)
 *     .headers(IdempotencyKeys.header(key))
 *     .body(request)
 *     .retrieve();
 * }</pre>
 */
public final class IdempotencyKeys {
  public static final String HEADER = "Idempotency-Key";

  private IdempotencyKeys() {}

  public static Consumer<HttpHeaders> header(IdempotencyKey key) {
    return headers -> headers.set(HEADER, key.value());
  }
}
