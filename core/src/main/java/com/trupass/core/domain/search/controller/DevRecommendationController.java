package com.trupass.core.domain.search.controller;

import com.trupass.core.domain.search.dto.SearchReqDTO;
import com.trupass.core.domain.search.dto.SearchResDTO;
import com.trupass.core.domain.search.service.TripRecommendationService;
import com.trupass.core.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 스파이크 확인용. 로컬 프로필에서만 뜬다. 문장 3개를 받아 한 번에(동기) 추천 조합까지 돌려준다.
 *
 * <p>실제 API는 엔드포인트 DB의 {@code POST /searches}(비동기 + 진행 상태 폴링)이고, M3에서 그쪽으로 옮긴다.
 */
@Profile("local")
@RestController
@RequestMapping("/internal/dev")
@Tag(name = "dev-search", description = "로컬 확인용: 검색 + AI 조합 추천 한 번에")
public class DevRecommendationController {
  private final TripRecommendationService service;

  public DevRecommendationController(TripRecommendationService service) {
    this.service = service;
  }

  @PostMapping("/trip-recommendations")
  @Operation(
      summary = "추천 조합 (로컬 확인용)",
      description =
          "AI 조건 추출 → Duffel 테스트 항공 + LiteAPI 샌드박스 숙소 동시 검색 → AI 조합 추천 → core 합계 계산. "
              + "20–40초 걸릴 수 있다. 가격은 실제 판매가가 아니다.")
  public ApiResponse<SearchResDTO.Recommendation> recommend(
      @Valid @RequestBody SearchReqDTO.Recommend request) {
    return ApiResponse.onSuccess(service.recommend(request));
  }
}
