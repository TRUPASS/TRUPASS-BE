package com.trupass.core.config.http

import com.trupass.core.domain.checkout.CheckoutId
import com.trupass.core.domain.checkout.IdempotencyKey
import com.trupass.core.domain.checkout.MerchantId
import com.trupass.core.domain.saga.SagaStepType
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.http.HttpMethod
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals

class RestClientConfigTest {
    @Test
    fun `멱등키는 checkout_id-merchant-step 형식이다`() {
        val key = IdempotencyKey.of(CheckoutId("co_1"), MerchantId("A"), SagaStepType.HOLD)
        assertEquals("co_1:A:HOLD", key.value)
    }

    @Test
    fun `MDC의 checkout_id와 멱등키가 헤더로 나간다`() {
        val builder = RestClient.builder().requestInterceptor(CheckoutContextInterceptor())
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = builder.baseUrl("http://merchant.test").build()
        val key = IdempotencyKey.of(CheckoutId("co_1"), MerchantId("A"), SagaStepType.HOLD)

        server
            .expect(requestTo("http://merchant.test/merchants/A/holds"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(CheckoutMdc.HEADER, "co_1"))
            .andExpect(header(IDEMPOTENCY_KEY_HEADER, "co_1:A:HOLD"))
            .andRespond(withSuccess())

        MDC.putCloseable(CheckoutMdc.KEY, "co_1").use {
            client
                .post()
                .uri("/merchants/{id}/holds", "A")
                .idempotencyKey(key)
                .retrieve()
                .toBodilessEntity()
        }
        server.verify()
    }
}
