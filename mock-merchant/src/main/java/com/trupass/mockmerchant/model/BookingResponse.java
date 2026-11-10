package com.trupass.mockmerchant.model;

import java.time.Instant;

public record BookingResponse(
    String bookingId,
    String holdId,
    String merchantId,
    BookingStatus status,
    long amount,
    String currency,
    Instant updatedAt) {

  public BookingResponse cancelled(Instant at) {
    return new BookingResponse(
        bookingId, holdId, merchantId, BookingStatus.CANCELLED, amount, currency, at);
  }
}
