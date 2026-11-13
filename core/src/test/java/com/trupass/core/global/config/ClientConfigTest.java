package com.trupass.core.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.trupass.core.domain.mandate.client.AuditClient;
import com.trupass.core.domain.mandate.client.localfile.LocalFileAuditClient;
import com.trupass.core.domain.mandate.client.s3.S3AuditClient;
import com.trupass.core.domain.merchant.client.MerchantClient;
import com.trupass.core.domain.merchant.client.duffel.DuffelMerchantClient;
import com.trupass.core.domain.merchant.client.mock.MockMerchantClient;
import com.trupass.core.domain.payment.client.PaymentClient;
import com.trupass.core.domain.payment.client.mock.MockPgClient;
import com.trupass.core.domain.payment.client.toss.TossPaymentClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

class ClientConfigTest {

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(TrupassProperties.class)
  static class PropertiesConfig {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(PropertiesConfig.class, ClientConfig.class)
          .withBean(RestClient.Builder.class, RestClient::builder);

  @Test
  void 기본값은_Mock_구현체다() {
    runner.run(context -> {
      assertThat(context.getBean(MerchantClient.class)).isInstanceOf(MockMerchantClient.class);
      assertThat(context.getBean(PaymentClient.class)).isInstanceOf(MockPgClient.class);
      assertThat(context.getBean(AuditClient.class)).isInstanceOf(LocalFileAuditClient.class);
    });
  }

  @Test
  void 설정_한_줄로_실제_구현체로_바뀐다() {
    runner
        .withPropertyValues(
            "trupass.clients.merchant=duffel",
            "trupass.clients.payment=toss",
            "trupass.clients.audit=s3")
        .run(context -> {
          assertThat(context.getBean(MerchantClient.class))
              .isInstanceOf(DuffelMerchantClient.class);
          assertThat(context.getBean(PaymentClient.class)).isInstanceOf(TossPaymentClient.class);
          assertThat(context.getBean(AuditClient.class)).isInstanceOf(S3AuditClient.class);
        });
  }

  @Test
  void 오타가_있으면_애플리케이션이_뜨지_않는다() {
    runner
        .withPropertyValues("trupass.clients.payment=tosss")
        .run(context -> assertThat(context).hasFailed());
  }
}
