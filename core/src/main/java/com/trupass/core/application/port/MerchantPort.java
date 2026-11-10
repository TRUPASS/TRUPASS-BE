package com.trupass.core.application.port;

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

/**
 * 판매자(항공·숙소·체험)와 통신하는 포트.
 *
 * <p>어댑터: {@code trupass.adapters.merchant=mock}(Mock 판매자 HTTP) | {@code duffel}(Duffel 항공·Stays).
 *
 * <p>공통 계약
 *
 * <ul>
 *   <li>상태를 바꾸는 모든 호출은 {@link IdempotencyKey}를 {@code Idempotency-Key} 헤더로 보낸다. 같은 키로 재시도하면 판매자는 첫
 *       응답을 돌려준다.
 *   <li>실패는 {@link com.trupass.core.domain.merchant.MerchantFailureException}으로 던지고, 사유로 재시도/보상 여부를
 *       판단한다.
 * </ul>
 */
public interface MerchantPort {

  /** 조건에 맞는 오퍼를 조회한다. 부작용이 없으므로 멱등키가 필요 없다. */
  List<Offer> searchOffers(OfferSearchCriteria criteria);

  /** 재고를 잡는다(Saga HOLD). 돈은 움직이지 않는다. 재고가 없으면 SOLD_OUT으로 실패한다. */
  Hold hold(HoldRequest request);

  /** HOLD의 보상. 잡아 둔 재고를 놓는다. 이미 해제·만료된 hold에도 성공해야 한다(멱등). */
  void release(MerchantId merchantId, HoldId holdId, IdempotencyKey idempotencyKey);

  /** Trupass → 판매자 정산(Saga PAY). Duffel은 잔액 결제, Mock은 가짜 정산이다. */
  MerchantPayment pay(MerchantPayRequest request);

  /** 결제된 hold를 예약으로 확정한다(Saga CONFIRM). */
  Booking confirm(MerchantId merchantId, HoldId holdId, IdempotencyKey idempotencyKey);

  /** 확정된 예약을 취소한다(CONFIRM/PAY의 보상). 판매자 환불 금액이 있으면 함께 돌려준다. */
  BookingCancellation cancel(
      MerchantId merchantId, BookingId bookingId, IdempotencyKey idempotencyKey);
}
