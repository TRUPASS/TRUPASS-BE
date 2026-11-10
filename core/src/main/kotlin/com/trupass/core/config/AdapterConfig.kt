package com.trupass.core.config

import com.trupass.core.adapter.out.audit.localfile.LocalFileAuditAdapter
import com.trupass.core.adapter.out.audit.s3.S3ObjectLockAuditAdapter
import com.trupass.core.adapter.out.merchant.duffel.DuffelMerchantAdapter
import com.trupass.core.adapter.out.merchant.mock.MockMerchantAdapter
import com.trupass.core.adapter.out.payment.mockpg.MockPgPaymentAdapter
import com.trupass.core.adapter.out.payment.toss.TossPaymentAdapter
import com.trupass.core.application.port.AuditPort
import com.trupass.core.application.port.MerchantPort
import com.trupass.core.application.port.PaymentPort
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

/**
 * 포트별 어댑터를 설정 한 줄로 고른다.
 *
 * - `trupass.adapters.merchant` = mock | duffel
 * - `trupass.adapters.payment`  = mock | toss
 * - `trupass.adapters.audit`    = localfile | s3
 *
 * SagaStatePort는 지금 Postgres 구현 하나뿐이라 선택지가 없다(Temporal 도입 시 여기에 추가).
 */
@Configuration(proxyBeanMethods = false)
class AdapterConfig {
    @Bean
    @ConditionalOnProperty(name = ["trupass.adapters.merchant"], havingValue = "mock", matchIfMissing = true)
    fun mockMerchantAdapter(
        restClientBuilder: RestClient.Builder,
        properties: TrupassProperties,
    ): MerchantPort =
        MockMerchantAdapter(
            restClientBuilder.clone().baseUrl(properties.services.mockMerchantBaseUrl).build(),
        )

    @Bean
    @ConditionalOnProperty(name = ["trupass.adapters.merchant"], havingValue = "duffel")
    fun duffelMerchantAdapter(): MerchantPort = DuffelMerchantAdapter()

    @Bean
    @ConditionalOnProperty(name = ["trupass.adapters.payment"], havingValue = "mock", matchIfMissing = true)
    fun mockPgPaymentAdapter(): PaymentPort = MockPgPaymentAdapter()

    @Bean
    @ConditionalOnProperty(name = ["trupass.adapters.payment"], havingValue = "toss")
    fun tossPaymentAdapter(): PaymentPort = TossPaymentAdapter()

    @Bean
    @ConditionalOnProperty(name = ["trupass.adapters.audit"], havingValue = "localfile", matchIfMissing = true)
    fun localFileAuditAdapter(properties: TrupassProperties): AuditPort = LocalFileAuditAdapter(properties.audit.localDir)

    @Bean
    @ConditionalOnProperty(name = ["trupass.adapters.audit"], havingValue = "s3")
    fun s3AuditAdapter(): AuditPort = S3ObjectLockAuditAdapter()
}
