package com.trupass.core.adapter.outbound.merchant.mock;

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
import org.springframework.web.client.RestClient;

/**
 * {@code mock-merchant} 서비스(HTTP)를 부르는 어댑터. 공통 RestClient(타임아웃·checkout_id 로깅)를 주입받는다. 멱등키는 {@code
 * IdempotencyKeys.header(key)}로 붙인다.
 */
public class MockMerchantAdapter implements MerchantPort {
  private final RestClient restClient;

  public MockMerchantAdapter(RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public List<Offer> searchOffers(OfferSearchCriteria criteria) {
    throw new UnsupportedOperationException("담당자 구현: GET /merchants/{id}/offers");
  }

  @Override
  public Hold hold(HoldRequest request) {
    throw new UnsupportedOperationException("담당자 구현: POST /merchants/{id}/holds");
  }

  @Override
  public void release(MerchantId merchantId, HoldId holdId, IdempotencyKey idempotencyKey) {
    throw new UnsupportedOperationException("담당자 구현: POST /holds/{holdId}/release");
  }

  @Override
  public MerchantPayment pay(MerchantPayRequest request) {
    throw new UnsupportedOperationException("담당자 구현: POST /holds/{holdId}/pay");
  }

  @Override
  public Booking confirm(MerchantId merchantId, HoldId holdId, IdempotencyKey idempotencyKey) {
    throw new UnsupportedOperationException("담당자 구현: POST /holds/{holdId}/confirm");
  }

  @Override
  public BookingCancellation cancel(
      MerchantId merchantId, BookingId bookingId, IdempotencyKey idempotencyKey) {
    throw new UnsupportedOperationException("담당자 구현: POST /bookings/{id}/cancel");
  }
}
