package com.trupass.mockmerchant

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@SpringBootTest
@AutoConfigureMockMvc
class MockMerchantApiTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var clock: MutableClock

    @BeforeEach
    fun resetClock() {
        clock.instant = Instant.parse("2026-01-01T00:00:00Z")
    }

    @Test
    fun `판매자별 오퍼를 조회한다`() {
        mockMvc.get("/merchants/A/offers").andExpect {
            status { isOk() }
            jsonPath("$[0].merchantId") { value("A") }
        }
        mockMvc.get("/merchants/Z/offers").andExpect { status { isNotFound() } }
    }

    @Test
    fun `X-Mock-Fail SOLD_OUT으로 hold를 요청하면 409가 온다`() {
        hold(key = null, fail = "SOLD_OUT").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("SOLD_OUT") }
        }
    }

    @Test
    fun `같은 Idempotency-Key로 두 번 hold하면 같은 응답이 온다`() {
        val key = newKey()
        val first = hold(key).andExpect { status { isCreated() } }.andReturn().response
        val second =
            hold(key)
                .andExpect {
                    status { isCreated() }
                    header { string(IdempotencyFilter.REPLAYED_HEADER, "true") }
                }.andReturn()
                .response

        assertEquals(first.contentAsString, second.contentAsString)

        val otherKey = hold(newKey()).andReturn().response
        assertNotEquals(first.contentAsString, otherKey.contentAsString, "다른 키는 새 hold를 만든다")
    }

    @Test
    fun `첫 응답이 SOLD_OUT이면 같은 키 재요청도 SOLD_OUT을 재생한다`() {
        val key = newKey()
        hold(key, fail = "SOLD_OUT").andExpect { status { isConflict() } }
        hold(key).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("SOLD_OUT") }
        }
    }

    @Test
    fun `5xx는 저장하지 않아 같은 키로 재시도하면 성공한다`() {
        val key = newKey()
        hold(key, fail = "ERROR_500").andExpect { status { isInternalServerError() } }
        hold(key).andExpect { status { isCreated() } }
    }

    @Test
    fun `같은 키를 다른 요청에 쓰면 422`() {
        val key = newKey()
        hold(key, offerId = "A-ICN-KIX-001").andExpect { status { isCreated() } }
        hold(key, offerId = "A-ICN-FUK-001").andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.code") { value("IDEMPOTENCY_KEY_REUSED") }
        }
    }

    @Test
    fun `hold → pay → confirm → cancel 정상 흐름`() {
        val holdId = holdIdOf(hold(newKey()))
        mockMvc
            .post("/holds/$holdId/pay") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"amount":320000,"currency":"KRW"}"""
            }.andExpect { jsonPath("$.status") { value("PAID") } }
        val bookingId =
            mockMvc
                .post("/holds/$holdId/confirm")
                .andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString
                .let { Regex("\"bookingId\":\"([^\"]+)\"").find(it)!!.groupValues[1] }
        mockMvc.post("/bookings/$bookingId/cancel").andExpect { jsonPath("$.status") { value("CANCELLED") } }
    }

    @Test
    fun `hold는 10분 뒤 만료되어 pay할 수 없고 release는 여전히 성공한다`() {
        val holdId = holdIdOf(hold(newKey()))
        clock.instant = clock.instant.plus(Duration.ofMinutes(10))

        mockMvc
            .post("/holds/$holdId/pay") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"amount":320000,"currency":"KRW"}"""
            }.andExpect {
                status { isGone() }
                jsonPath("$.code") { value("HOLD_EXPIRED") }
            }
        mockMvc.post("/holds/$holdId/release").andExpect { jsonPath("$.status") { value("RELEASED") } }
    }

    private fun hold(
        key: String?,
        fail: String? = null,
        offerId: String = "A-ICN-KIX-001",
    ): ResultActionsDsl =
        mockMvc.post("/merchants/A/holds") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"offerId":"$offerId","quantity":1}"""
            key?.let { header(IdempotencyFilter.HEADER, it) }
            fail?.let { header(FailureInjectionFilter.FAIL_HEADER, it) }
        }

    private fun holdIdOf(result: ResultActionsDsl): String =
        Regex("\"holdId\":\"([^\"]+)\"").find(result.andReturn().response.contentAsString)!!.groupValues[1]

    private fun newKey() = "co_${UUID.randomUUID()}:A:HOLD"

    @TestConfiguration
    class ClockConfig {
        @Bean
        @Primary
        fun mutableClock() = MutableClock()
    }
}

class MutableClock(
    var instant: Instant = Instant.EPOCH,
) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = instant
}
