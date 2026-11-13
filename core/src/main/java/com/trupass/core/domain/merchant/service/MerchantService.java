package com.trupass.core.domain.merchant.service;

import com.trupass.core.domain.merchant.client.MerchantClient;
import com.trupass.core.domain.merchant.client.dto.MerchantClientReqDTO;
import com.trupass.core.domain.merchant.dto.MerchantReqDTO;
import com.trupass.core.domain.merchant.dto.MerchantResDTO;
import org.springframework.stereotype.Service;

/**
 * 판매자 연동. 상품 검색과 자리 잡기·대금 지급·예약 확정·취소를 {@link MerchantClient}로 부른다. 다른 도메인(checkout, trip)은 이
 * service를 통해서만 판매자와 통신한다.
 */
@Service
public class MerchantService {
  private final MerchantClient merchantClient;

  public MerchantService(MerchantClient merchantClient) {
    this.merchantClient = merchantClient;
  }

  public MerchantResDTO.SearchOffers searchOffers(MerchantReqDTO.SearchOffers request) {
    var criteria =
        new MerchantClientReqDTO.SearchOffers(
            request.destination(),
            request.startDate(),
            request.endDate(),
            request.travelers(),
            request.categories());
    var offers = merchantClient.searchOffers(criteria).stream().map(MerchantResDTO.Offer::from);
    return new MerchantResDTO.SearchOffers(offers.toList());
  }
}
