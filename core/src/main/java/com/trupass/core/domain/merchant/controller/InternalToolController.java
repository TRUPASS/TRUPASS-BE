package com.trupass.core.domain.merchant.controller;

import com.trupass.core.domain.merchant.dto.MerchantReqDTO;
import com.trupass.core.domain.merchant.dto.MerchantResDTO;
import com.trupass.core.domain.merchant.service.MerchantService;
import com.trupass.core.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * agent 전용 내부 도구 API. agent는 외부 API(Duffel, TourAPI 등)를 직접 부르지 않고 여기만 호출한다. 운영 환경에서는 Caddy가 {@code
 * /internal} 하위 경로를 외부에 열지 않는다.
 */
@RestController
@RequestMapping("/internal/tools")
@Tag(name = "internal-tools", description = "agent 전용 내부 도구")
public class InternalToolController {
  private final MerchantService merchantService;

  public InternalToolController(MerchantService merchantService) {
    this.merchantService = merchantService;
  }

  @PostMapping("/search-offers")
  @Operation(summary = "상품 검색", description = "MerchantClient 구현 전까지는 501(COMMON501)을 돌려준다.")
  public ApiResponse<MerchantResDTO.SearchOffers> searchOffers(
      @Valid @RequestBody MerchantReqDTO.SearchOffers request) {
    return ApiResponse.onSuccess(merchantService.searchOffers(request));
  }
}
