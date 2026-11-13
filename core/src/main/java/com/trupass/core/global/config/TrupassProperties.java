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
    @DefaultValue Audit audit,
    @DefaultValue Auth auth) {

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

  /** 로그인·토큰 설정. */
  public record Auth(@DefaultValue Google google, @DefaultValue Jwt jwt) {}

  /**
   * Google 로그인. {@code clientIds}는 Google Cloud Console의 OAuth 클라이언트 ID다(웹·iOS·Android가 다르면 쉼표로 여러
   * 개). ID 토큰의 {@code aud}가 이 중 하나여야 한다.
   */
  public record Google(@DefaultValue List<String> clientIds) {}

  /**
   * Trupass가 발급하는 토큰. {@code secret}은 HS256 서명 키로 32바이트 이상이어야 한다. access token은 JWT, refresh token은
   * 랜덤 문자열(DB에는 해시만 저장)이다.
   *
   * <p>{@code localDevSecret}은 local 프로필에만 있는 개발용 키다. {@code secret}이 없거나 빈 값이면 이 값을 쓴다. {@code
   * .env}에 {@code TRUPASS_JWT_SECRET=}처럼 빈 값으로 있어도 로컬에서 뜨게 하려는 것이다.
   */
  public record Jwt(
      String secret,
      String localDevSecret,
      @DefaultValue("trupass") String issuer,
      @DefaultValue("30m") Duration accessTokenTtl,
      @DefaultValue("14d") Duration refreshTokenTtl) {}
}
