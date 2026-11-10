package com.trupass.mockmerchant.model;

import java.time.Instant;

public record HoldResponse(
    String holdId,
    String merchantId,
    String offerId,
    int quantity,
    long amount,
    String currency,
    HoldStatus status,
    Instant expiresAt) {

  public HoldResponse withStatus(HoldStatus newStatus) {
    return new HoldResponse(
        holdId, merchantId, offerId, quantity, amount, currency, newStatus, expiresAt);
  }
}
