package com.trupass.core.config;

import com.trupass.core.adapter.outbound.audit.localfile.LocalFileAuditAdapter;
import com.trupass.core.adapter.outbound.audit.s3.S3ObjectLockAuditAdapter;
import com.trupass.core.adapter.outbound.merchant.duffel.DuffelMerchantAdapter;
import com.trupass.core.adapter.outbound.merchant.mock.MockMerchantAdapter;
import com.trupass.core.adapter.outbound.payment.mockpg.MockPgPaymentAdapter;
import com.trupass.core.adapter.outbound.payment.toss.TossPaymentAdapter;
import com.trupass.core.application.port.AuditPort;
import com.trupass.core.application.port.MerchantPort;
import com.trupass.core.application.port.PaymentPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 포트별 어댑터를 설정 한 줄로 고른다.
 *
 * <ul>
 *   <li>{@code trupass.adapters.merchant} = mock | duffel
 *   <li>{@code trupass.adapters.payment} = mock | toss
 *   <li>{@code trupass.adapters.audit} = localfile | s3
 * </ul>
 *
 * <p>SagaStatePort는 지금 Postgres 구현 하나뿐이라 선택지가 없다(Temporal 도입 시 여기에 추가).
 */
@Configuration(proxyBeanMethods = false)
public class AdapterConfig {

  @Bean
  @ConditionalOnProperty(
      name = "trupass.adapters.merchant",
      havingValue = "mock",
      matchIfMissing = true)
  MerchantPort mockMerchantAdapter(
      RestClient.Builder restClientBuilder, TrupassProperties properties) {
    return new MockMerchantAdapter(
        restClientBuilder.clone().baseUrl(properties.services().mockMerchantBaseUrl()).build());
  }

  @Bean
  @ConditionalOnProperty(name = "trupass.adapters.merchant", havingValue = "duffel")
  MerchantPort duffelMerchantAdapter() {
    return new DuffelMerchantAdapter();
  }

  @Bean
  @ConditionalOnProperty(
      name = "trupass.adapters.payment",
      havingValue = "mock",
      matchIfMissing = true)
  PaymentPort mockPgPaymentAdapter() {
    return new MockPgPaymentAdapter();
  }

  @Bean
  @ConditionalOnProperty(name = "trupass.adapters.payment", havingValue = "toss")
  PaymentPort tossPaymentAdapter() {
    return new TossPaymentAdapter();
  }

  @Bean
  @ConditionalOnProperty(
      name = "trupass.adapters.audit",
      havingValue = "localfile",
      matchIfMissing = true)
  AuditPort localFileAuditAdapter(TrupassProperties properties) {
    return new LocalFileAuditAdapter(properties.audit().localDir());
  }

  @Bean
  @ConditionalOnProperty(name = "trupass.adapters.audit", havingValue = "s3")
  AuditPort s3AuditAdapter() {
    return new S3ObjectLockAuditAdapter();
  }
}
