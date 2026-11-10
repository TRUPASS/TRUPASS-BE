package com.trupass.mockmerchant

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("mock")
data class MockMerchantProperties(
    /** hold가 자동 만료되기까지의 시간. */
    val holdTtl: Duration = Duration.ofMinutes(10),
    /** `X-Mock-Fail: TIMEOUT`일 때 응답 전에 멈춰 있는 시간. core의 읽기 타임아웃(5s)보다 길어야 한다. */
    val timeoutDelay: Duration = Duration.ofSeconds(30),
)
