package com.trupass.mockmerchant

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 요청 헤더로 실패를 주입한다. 판매자 API 경로(`/merchants`, `/holds`, `/bookings`)에만 적용된다.
 *
 * - `X-Mock-Delay-Ms: 3000` → 응답 전에 3초 지연
 * - `X-Mock-Fail: SOLD_OUT` → 409 `{"code":"SOLD_OUT"}`
 * - `X-Mock-Fail: TIMEOUT` → `mock.timeout-delay`(기본 30초) 동안 멈춘 뒤 504
 * - `X-Mock-Fail: ERROR_500` → 500
 *
 * [IdempotencyFilter]보다 안쪽에서 돈다. 그래서 같은 멱등키로 재요청하면 실패 주입 헤더와 상관없이 첫 응답이 재생된다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class FailureInjectionFilter(
    private val properties: MockMerchantProperties,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = MERCHANT_PATHS.none { request.requestURI.startsWith(it) }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        request
            .getHeader(DELAY_HEADER)
            ?.toLongOrNull()
            ?.takeIf { it > 0 }
            ?.let { Thread.sleep(it) }

        val failure = request.getHeader(FAIL_HEADER)?.trim()?.uppercase()
        when (failure) {
            null, "" -> {
                filterChain.doFilter(request, response)
            }

            "SOLD_OUT" -> {
                writeError(response, HttpStatus.CONFLICT, "SOLD_OUT", "injected: sold out")
            }

            "TIMEOUT" -> {
                Thread.sleep(properties.timeoutDelay.toMillis())
                writeError(response, HttpStatus.GATEWAY_TIMEOUT, "TIMEOUT", "injected: timeout")
            }

            "ERROR_500" -> {
                writeError(response, HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_500", "injected: internal error")
            }

            else -> {
                writeError(response, HttpStatus.BAD_REQUEST, "UNKNOWN_MOCK_FAIL", "unknown $FAIL_HEADER value: $failure")
            }
        }
    }

    private fun writeError(
        response: HttpServletResponse,
        status: HttpStatus,
        code: String,
        message: String,
    ) {
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write("""{"code":"$code","message":"$message"}""")
    }

    companion object {
        const val FAIL_HEADER = "X-Mock-Fail"
        const val DELAY_HEADER = "X-Mock-Delay-Ms"
        val MERCHANT_PATHS = listOf("/merchants", "/holds", "/bookings")
    }
}
