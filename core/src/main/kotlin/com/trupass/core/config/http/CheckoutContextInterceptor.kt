package com.trupass.core.config.http

import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

/**
 * 외부 호출마다 현재 MDC의 `checkout_id`를 `X-Checkout-Id` 헤더로 전파하고,
 * 메서드·URI·상태·소요 시간·멱등키를 구조화 로그로 남긴다.
 *
 * MDC에 checkout_id를 넣는 쪽: 들어오는 요청은 `CheckoutIdMdcFilter`, Saga 실행은 오케스트레이터가
 * `MDC.putCloseable(CheckoutMdc.KEY, id)`로 감싼다.
 */
class CheckoutContextInterceptor : ClientHttpRequestInterceptor {
    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        val checkoutId = MDC.get(CheckoutMdc.KEY)
        if (checkoutId != null && !request.headers.containsHeader(CheckoutMdc.HEADER)) {
            request.headers.set(CheckoutMdc.HEADER, checkoutId)
        }
        val idempotencyKey = request.headers.getFirst(IDEMPOTENCY_KEY_HEADER)
        val startedAt = System.nanoTime()
        try {
            val response = execution.execute(request, body)
            log
                .atInfo()
                .addKeyValue("http.method", request.method.name())
                .addKeyValue("url", request.uri.toString())
                .addKeyValue("http.status", response.statusCode.value())
                .addKeyValue("duration_ms", elapsedMs(startedAt))
                .addKeyValue("idempotency_key", idempotencyKey)
                .log("outbound http call")
            return response
        } catch (e: Exception) {
            log
                .atWarn()
                .addKeyValue("http.method", request.method.name())
                .addKeyValue("url", request.uri.toString())
                .addKeyValue("duration_ms", elapsedMs(startedAt))
                .addKeyValue("idempotency_key", idempotencyKey)
                .setCause(e)
                .log("outbound http call failed")
            throw e
        }
    }

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000

    private companion object {
        private val log = LoggerFactory.getLogger(CheckoutContextInterceptor::class.java)
    }
}

object CheckoutMdc {
    const val KEY = "checkout_id"
    const val HEADER = "X-Checkout-Id"
}
