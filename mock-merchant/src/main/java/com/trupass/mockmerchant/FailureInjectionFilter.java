package com.trupass.mockmerchant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청 헤더로 실패를 주입한다. 판매자 API 경로({@code /merchants}, {@code /holds}, {@code /bookings})에만 적용된다.
 *
 * <ul>
 *   <li>{@code X-Mock-Delay-Ms: 3000} → 응답 전에 3초 지연
 *   <li>{@code X-Mock-Fail: SOLD_OUT} → 409 {@code {"code":"SOLD_OUT"}}
 *   <li>{@code X-Mock-Fail: TIMEOUT} → {@code mock.timeout-delay}(기본 30초) 동안 멈춘 뒤 504
 *   <li>{@code X-Mock-Fail: ERROR_500} → 500
 * </ul>
 *
 * <p>{@link IdempotencyFilter}보다 안쪽에서 돈다. 그래서 같은 멱등키로 재요청하면 실패 주입 헤더와 상관없이 첫 응답이 재생된다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class FailureInjectionFilter extends OncePerRequestFilter {
  public static final String FAIL_HEADER = "X-Mock-Fail";
  public static final String DELAY_HEADER = "X-Mock-Delay-Ms";
  static final List<String> MERCHANT_PATHS = List.of("/merchants", "/holds", "/bookings");

  private final MockMerchantProperties properties;

  public FailureInjectionFilter(MockMerchantProperties properties) {
    this.properties = properties;
  }

  static boolean isMerchantPath(HttpServletRequest request) {
    return MERCHANT_PATHS.stream().anyMatch(request.getRequestURI()::startsWith);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !isMerchantPath(request);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long delayMs = parseLong(request.getHeader(DELAY_HEADER));
    if (delayMs > 0) {
      sleep(delayMs);
    }

    String failure = request.getHeader(FAIL_HEADER);
    failure = failure == null ? "" : failure.trim().toUpperCase(Locale.ROOT);
    switch (failure) {
      case "" -> filterChain.doFilter(request, response);
      case "SOLD_OUT" ->
          JsonErrors.write(response, HttpStatus.CONFLICT, "SOLD_OUT", "injected: sold out");
      case "TIMEOUT" -> {
        sleep(properties.timeoutDelay().toMillis());
        JsonErrors.write(response, HttpStatus.GATEWAY_TIMEOUT, "TIMEOUT", "injected: timeout");
      }
      case "ERROR_500" ->
          JsonErrors.write(
              response, HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_500", "injected: internal error");
      default ->
          JsonErrors.write(
              response,
              HttpStatus.BAD_REQUEST,
              "UNKNOWN_MOCK_FAIL",
              "unknown " + FAIL_HEADER + " value");
    }
  }

  private static long parseLong(String value) {
    try {
      return value == null ? 0 : Long.parseLong(value.trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
