package com.trupass.core.domain.merchant.client.duffel;

import com.trupass.core.domain.merchant.client.MerchantClient;
import com.trupass.core.domain.merchant.client.dto.MerchantClientReqDTO;
import com.trupass.core.domain.merchant.client.dto.MerchantClientResDTO;
import com.trupass.core.global.common.IdempotencyKey;
import java.util.List;

/** Duffel 항공·Stays 구현체 자리. 키는 환경 변수 {@code DUFFEL_ACCESS_TOKEN}으로 받는다. */
public class DuffelMerchantClient implements MerchantClient {
  @Override
  public List<MerchantClientResDTO.Offer> searchOffers(MerchantClientReqDTO.SearchOffers request) {
    throw new UnsupportedOperationException("Duffel 연동 예정: 상품 검색");
  }

  @Override
  public MerchantClientResDTO.Hold hold(MerchantClientReqDTO.Hold request) {
    throw new UnsupportedOperationException("Duffel 연동 예정: 자리 잡기");
  }

  @Override
  public void release(String merchantId, String holdId, IdempotencyKey idempotencyKey) {
    throw new UnsupportedOperationException("Duffel 연동 예정: hold 해제");
  }

  @Override
  public MerchantClientResDTO.Payment pay(MerchantClientReqDTO.Pay request) {
    throw new UnsupportedOperationException("Duffel 연동 예정: 대금 지급");
  }

  @Override
  public MerchantClientResDTO.Booking confirm(
      String merchantId, String holdId, IdempotencyKey idempotencyKey) {
    throw new UnsupportedOperationException("Duffel 연동 예정: 예약 확정");
  }

  @Override
  public MerchantClientResDTO.Cancellation cancel(
      String merchantId, String bookingId, IdempotencyKey idempotencyKey) {
    throw new UnsupportedOperationException("Duffel 연동 예정: 예약 취소");
  }
}
