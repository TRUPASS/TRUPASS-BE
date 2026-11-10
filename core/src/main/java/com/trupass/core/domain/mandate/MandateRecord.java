package com.trupass.core.domain.mandate;

import com.trupass.core.domain.checkout.CheckoutId;
import java.time.Instant;

/** 사용자가 패스키로 서명한 AP2 Mandate 원문. 검증 로직은 WebAuthn 라이브러리 결정 후 추가한다. */
public record MandateRecord(
    CheckoutId checkoutId, String mandateId, String contentType, byte[] payload, Instant signedAt) {
  public MandateRecord {
    payload = payload.clone();
  }

  @Override
  public byte[] payload() {
    return payload.clone();
  }
}
