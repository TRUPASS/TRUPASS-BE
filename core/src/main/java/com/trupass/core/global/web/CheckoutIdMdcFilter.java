package com.trupass.core.global.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 들어오는 요청의 {@code X-Checkout-Id} 헤더를 MDC {@code checkout_id}에 넣어, 그 요청의 모든 로그에 남게 한다. */
@Component
public class CheckoutIdMdcFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String checkoutId = request.getHeader(CheckoutMdc.HEADER);
    if (checkoutId == null || checkoutId.isBlank()) {
      filterChain.doFilter(request, response);
      return;
    }
    try (MDC.MDCCloseable ignored = MDC.putCloseable(CheckoutMdc.KEY, checkoutId)) {
      filterChain.doFilter(request, response);
    }
  }
}
