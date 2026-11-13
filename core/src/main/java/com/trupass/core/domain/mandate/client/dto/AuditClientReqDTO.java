package com.trupass.core.domain.mandate.client.dto;

import java.time.Instant;

/** {@code AuditClient}에 넘기는 요청 형식. */
public final class AuditClientReqDTO {
  private AuditClientReqDTO() {}

  /** 사용자가 패스키로 서명한 AP2 Mandate 원문. */
  public record Mandate(
      String checkoutId, String mandateId, String contentType, byte[] payload, Instant signedAt) {
    public Mandate {
      payload = payload.clone();
    }

    @Override
    public byte[] payload() {
      return payload.clone();
    }
  }

  /** 판매자·PG가 발행한 영수증 원문. */
  public record Receipt(
      String checkoutId, String merchantId, String contentType, byte[] payload, Instant issuedAt) {
    public Receipt {
      payload = payload.clone();
    }

    @Override
    public byte[] payload() {
      return payload.clone();
    }
  }
}
