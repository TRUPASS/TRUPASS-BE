@file:Suppress("ktlint:standard:package-name") // `in`은 Kotlin 키워드라 백틱이 필요하다.

package com.trupass.core.adapter.`in`.web

import com.trupass.core.config.http.CheckoutMdc
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/** 들어오는 요청의 `X-Checkout-Id` 헤더를 MDC `checkout_id`에 넣어, 그 요청의 모든 로그에 남게 한다. */
@Component
class CheckoutIdMdcFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val checkoutId = request.getHeader(CheckoutMdc.HEADER)?.takeIf { it.isNotBlank() }
        if (checkoutId == null) {
            filterChain.doFilter(request, response)
            return
        }
        MDC.putCloseable(CheckoutMdc.KEY, checkoutId).use {
            filterChain.doFilter(request, response)
        }
    }
}
