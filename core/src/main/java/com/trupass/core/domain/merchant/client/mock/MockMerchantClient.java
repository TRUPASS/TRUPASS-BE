package com.trupass.core.domain.merchant.client.mock;

import com.trupass.core.domain.merchant.client.MerchantClient;
import com.trupass.core.domain.merchant.client.dto.MerchantClientReqDTO;
import com.trupass.core.domain.merchant.client.dto.MerchantClientResDTO;
import com.trupass.core.global.common.IdempotencyKey;
import java.util.List;
import org.springframework.web.client.RestClient;

/**
 * {@code mock-merchant} 서버(HTTP)를 부르는 구현체. 공통 RestClient(타임아웃·checkout_id 로깅)를 받는다. 멱등키는 {@code
 * .headers(key.asHeader())}로 붙인다.
 */
public class MockMerchantClient implements MerchantClient {
  private final RestClient restClient;

  public MockMerchantClient(RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public List<MerchantClientResDTO.Offer> searchOffers(MerchantClientReqDTO.SearchOffers request) {
    throw new UnsupportedOperationException("담당자 구현: GET /merchants/{id}/offers");
  }

  @Override
  public MerchantClientResDTO.Hold hold(MerchantClientReqDTO.Hold request) {
    throw new UnsupportedOperationException("담당자 구현: POST /merchants/{id}/holds");
  }

  @Override
  public void release(String merchantId, String holdId, IdempotencyKey idempotencyKey) {
    throw new UnsupportedOperationException("담당자 구현: POST /holds/{holdId}/release");
  }

  @Override
  public MerchantClientResDTO.Payment pay(MerchantClientReqDTO.Pay request) {
    throw new UnsupportedOperationException("담당자 구현: POST /holds/{holdId}/pay");
  }

  @Override
  public MerchantClientResDTO.Booking confirm(
      String merchantId, String holdId, IdempotencyKey idempotencyKey) {
    throw new UnsupportedOperationException("담당자 구현: POST /holds/{holdId}/confirm");
  }

  @Override
  public MerchantClientResDTO.Cancellation cancel(
      String merchantId, String bookingId, IdempotencyKey idempotencyKey) {
    throw new UnsupportedOperationException("담당자 구현: POST /bookings/{bookingId}/cancel");
  }
}
