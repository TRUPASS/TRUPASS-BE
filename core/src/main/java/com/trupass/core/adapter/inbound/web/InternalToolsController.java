package com.trupass.core.adapter.inbound.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * agent 전용 내부 도구 API. agent는 외부 API(Duffel, TourAPI 등)를 직접 부르지 않고 여기만 호출한다. 1단계 배포에서는 Caddy가 {@code
 * /internal} 하위 경로를 외부에 노출하지 않는다.
 */
@RestController
@RequestMapping("/internal/tools")
@Tag(name = "internal-tools", description = "agent 전용 내부 도구")
public class InternalToolsController {

  @PostMapping("/search-offers")
  @Operation(summary = "오퍼 검색", description = "아직 구현되지 않았다(501). MerchantPort.searchOffers에 연결 예정.")
  public ResponseEntity<SearchOffersResponse> searchOffers(
      @Valid @RequestBody SearchOffersRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
  }
}
