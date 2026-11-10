package com.trupass.core.adapter.outbound.merchant.duffel;

import com.trupass.core.application.port.MerchantPort;
import com.trupass.core.domain.checkout.IdempotencyKey;
import com.trupass.core.domain.checkout.MerchantId;
import com.trupass.core.domain.merchant.Booking;
import com.trupass.core.domain.merchant.BookingCancellation;
import com.trupass.core.domain.merchant.BookingId;
import com.trupass.core.domain.merchant.Hold;
import com.trupass.core.domain.merchant.HoldId;
import com.trupass.core.domain.merchant.HoldRequest;
import com.trupass.core.domain.merchant.MerchantPayRequest;
import com.trupass.core.domain.merchant.MerchantPayment;
import com.trupass.core.domain.pkg.Offer;
import com.trupass.core.domain.pkg.OfferSearchCriteria;
import java.util.List;

/** Duffel 항공·Stays 어댑터 자리. 2단계 이후 구현한다. */
public class DuffelMerchantAdapter implements MerchantPort {

  @Override
  public List<Offer> searchOffers(OfferSearchCriteria criteria) {
    throw new UnsupportedOperationException("Duffel 연동 예정: GET /merchants/{id}/offers");
  }

  @Override
  public Hold hold(HoldRequest request) {
    throw new UnsupportedOperationException("Duffel 연동 예정: POST /merchants/{id}/holds");
  }

  @Override
  public void release(MerchantId merchantId, HoldId holdId, IdempotencyKey idempotencyKey) {
    throw new UnsupportedOperationException("Duffel 연동 예정: POST /holds/{holdId}/release");
  }

  @Override
  public MerchantPayment pay(MerchantPayRequest request) {
    throw new UnsupportedOperationException("Duffel 연동 예정: POST /holds/{holdId}/pay");
  }

  @Override
  public Booking confirm(MerchantId merchantId, HoldId holdId, IdempotencyKey idempotencyKey) {
    throw new UnsupportedOperationException("Duffel 연동 예정: POST /holds/{holdId}/confirm");
  }

  @Override
  public BookingCancellation cancel(
      MerchantId merchantId, BookingId bookingId, IdempotencyKey idempotencyKey) {
    throw new UnsupportedOperationException("Duffel 연동 예정: POST /bookings/{id}/cancel");
  }
}
