package com.trupass.core.domain.merchant.client;

import com.trupass.core.domain.merchant.client.dto.MerchantClientReqDTO;
import com.trupass.core.domain.merchant.client.dto.MerchantClientResDTO;
import com.trupass.core.global.common.IdempotencyKey;
import java.util.List;

/**
 * 판매자(항공·숙소·체험) 연동. service는 이 인터페이스만 안다.
 *
 * <p>구현체: {@code trupass.clients.merchant=mock}(mock-merchant HTTP) | {@code duffel}(Duffel
 * 항공·Stays). 연결은 {@code global/config/ClientConfig}에서만 한다.
 *
 * <p>공통 계약
 *
 * <ul>
 *   <li>상태를 바꾸는 호출은 {@link IdempotencyKey}를 {@code Idempotency-Key} 헤더로 보낸다. 같은 키로 재시도하면 판매자는 첫 응답을
 *       돌려준다.
 *   <li>실패는 {@code GeneralException(MerchantErrorCode.XXX)}로 던지고, 코드로 재시도/되돌리기 여부를 판단한다.
 * </ul>
 */
public interface MerchantClient {

  /** 조건에 맞는 상품을 찾는다. 부작용이 없으므로 멱등키가 필요 없다. */
  List<MerchantClientResDTO.Offer> searchOffers(MerchantClientReqDTO.SearchOffers request);

  /** 재고를 잡는다(HOLD). 돈은 움직이지 않는다. 재고가 없으면 SOLD_OUT으로 실패한다. */
  MerchantClientResDTO.Hold hold(MerchantClientReqDTO.Hold request);

  /** HOLD 되돌리기. 잡아 둔 재고를 놓는다. 이미 해제·만료된 hold에도 성공해야 한다(멱등). */
  void release(String merchantId, String holdId, IdempotencyKey idempotencyKey);

  /** Trupass → 판매자 대금 지급(PAY). Duffel은 잔액 결제, Mock은 가짜 정산이다. */
  MerchantClientResDTO.Payment pay(MerchantClientReqDTO.Pay request);

  /** 대금을 지급한 hold를 예약으로 확정한다(CONFIRM). */
  MerchantClientResDTO.Booking confirm(
      String merchantId, String holdId, IdempotencyKey idempotencyKey);

  /** 확정된 예약을 취소한다(CONFIRM/PAY 되돌리기). 판매자 환불 금액이 있으면 함께 돌려준다. */
  MerchantClientResDTO.Cancellation cancel(
      String merchantId, String bookingId, IdempotencyKey idempotencyKey);
}
