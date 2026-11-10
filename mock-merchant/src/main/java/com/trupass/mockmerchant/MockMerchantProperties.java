package com.trupass.mockmerchant;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Mock 판매자 설정.
 *
 * @param holdTtl hold가 자동 만료되기까지의 시간.
 * @param timeoutDelay {@code X-Mock-Fail: TIMEOUT}일 때 응답 전에 멈춰 있는 시간. core의 읽기 타임아웃(5s)보다 길어야 한다.
 */
@ConfigurationProperties("mock")
public record MockMerchantProperties(
    @DefaultValue("10m") Duration holdTtl, @DefaultValue("30s") Duration timeoutDelay) {}
