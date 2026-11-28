package com.trupass.core.domain.search.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class SearchReqDTO {
  private SearchReqDTO() {}

  /**
   * 화면 05의 문장 3개.
   *
   * @param prompt 여행 조건(어디로, 언제, 몇 명, 예산).
   * @param accommodationPrompt 숙소 선호.
   * @param purposePrompt 여행 목적.
   * @param originHint 출발 도시. 비우면 AI가 문장에서 찾고, 그래도 없으면 서울.
   */
  public record Recommend(
      @NotBlank @Size(max = 3000) String prompt,
      @Size(max = 1000) String accommodationPrompt,
      @Size(max = 1000) String purposePrompt,
      @Size(max = 100) String originHint) {}
}
