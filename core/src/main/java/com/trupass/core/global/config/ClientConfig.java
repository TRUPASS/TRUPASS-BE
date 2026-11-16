package com.trupass.core.global.config;

import com.trupass.core.domain.auth.client.GoogleAuthClient;
import com.trupass.core.domain.auth.client.google.GoogleIdTokenClient;
import com.trupass.core.domain.mandate.client.AuditClient;
import com.trupass.core.domain.mandate.client.localfile.LocalFileAuditClient;
import com.trupass.core.domain.mandate.client.s3.S3AuditClient;
import com.trupass.core.domain.merchant.client.MerchantClient;
import com.trupass.core.domain.merchant.client.duffel.DuffelMerchantClient;
import com.trupass.core.domain.merchant.client.mock.MockMerchantClient;
import com.trupass.core.domain.payment.client.PaymentClient;
import com.trupass.core.domain.payment.client.mock.MockPgClient;
import com.trupass.core.domain.payment.client.toss.TossPaymentClient;
import com.trupass.core.domain.trip.client.AgentClient;
import com.trupass.core.domain.trip.client.http.HttpAgentClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 외부 연동 구현체를 설정 한 줄로 고른다. service는 인터페이스({@code XxxClient})만 알고, 구현체는 여기서만 연결한다(ArchUnit이 검사).
 *
 * <ul>
 *   <li>{@code trupass.clients.merchant} = mock | duffel
 *   <li>{@code trupass.clients.payment} = mock | toss
 *   <li>{@code trupass.clients.audit} = localfile | s3
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class ClientConfig {

  @Bean
  MerchantClient merchantClient(
      TrupassProperties properties, RestClient.Builder restClientBuilder) {
    return switch (properties.clients().merchant()) {
      case MOCK ->
          new MockMerchantClient(
              restClientBuilder
                  .clone()
                  .baseUrl(properties.services().mockMerchantBaseUrl())
                  .build());
      case DUFFEL -> new DuffelMerchantClient();
    };
  }

  @Bean
  PaymentClient paymentClient(TrupassProperties properties) {
    return switch (properties.clients().payment()) {
      case MOCK -> new MockPgClient();
      case TOSS -> new TossPaymentClient();
    };
  }

  @Bean
  AuditClient auditClient(TrupassProperties properties) {
    return switch (properties.clients().audit()) {
      case LOCALFILE -> new LocalFileAuditClient(properties.audit().localDir());
      case S3 -> new S3AuditClient();
    };
  }

  @Bean
  AgentClient agentClient(TrupassProperties properties, RestClient.Builder restClientBuilder) {
    return new HttpAgentClient(
        restClientBuilder.clone().baseUrl(properties.services().agentBaseUrl()).build());
  }

  @Bean
  GoogleAuthClient googleAuthClient(TrupassProperties properties) {
    return new GoogleIdTokenClient(
        properties.auth().google().clientIds(),
        properties.http().connectTimeout(),
        properties.http().readTimeout());
  }
}
