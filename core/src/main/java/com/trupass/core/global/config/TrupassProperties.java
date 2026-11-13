package com.trupass.core.global.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code trupass.*} 설정. 환경 변수로도 덮어쓸 수 있다(예: {@code TRUPASS_CLIENTS_MERCHANT=duffel}). client 값은
 * enum이라 오타가 있으면 애플리케이션이 뜨지 않는다.
 */
@ConfigurationProperties("trupass")
public record TrupassProperties(
    @DefaultValue Clients clients,
    @DefaultValue Services services,
    @DefaultValue Http http,
    @DefaultValue Cors cors,
    @DefaultValue Audit audit) {

  /** 외부 연동 구현체 선택. {@link ClientConfig}가 이 값을 보고 구현체를 하나 고른다. */
  public record Clients(
      @DefaultValue("mock") MerchantClientType merchant,
      @DefaultValue("mock") PaymentClientType payment,
      @DefaultValue("localfile") AuditClientType audit) {}

  public enum MerchantClientType {
    MOCK,
    DUFFEL
  }

  public enum PaymentClientType {
    MOCK,
    TOSS
  }

  public enum AuditClientType {
    LOCALFILE,
    S3
  }

  public record Services(
      @DefaultValue("http://localhost:9001") String mockMerchantBaseUrl,
      @DefaultValue("http://localhost:8001") String agentBaseUrl) {}

  /** 외부 HTTP 호출 공통 타임아웃. */
  public record Http(
      @DefaultValue("2s") Duration connectTimeout, @DefaultValue("5s") Duration readTimeout) {}

  public record Cors(@DefaultValue("http://localhost:5173") List<String> allowedOrigins) {}

  public record Audit(@DefaultValue("./data/audit") Path localDir) {}
}
