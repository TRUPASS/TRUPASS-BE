package com.trupass.core.domain.mandate;

import com.trupass.core.domain.checkout.CheckoutId;
import com.trupass.core.domain.checkout.MerchantId;
import java.time.Instant;

/** 판매자·PG가 발행한 영수증 원문. */
public record ReceiptRecord(
    CheckoutId checkoutId,
    MerchantId merchantId,
    String contentType,
    byte[] payload,
    Instant issuedAt) {
  public ReceiptRecord {
    payload = payload.clone();
  }

  @Override
  public byte[] payload() {
    return payload.clone();
  }
}
