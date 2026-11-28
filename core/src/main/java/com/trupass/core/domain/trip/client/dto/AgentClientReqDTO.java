package com.trupass.core.domain.trip.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** {@code AgentClient}에 넘기는 요청 형식. agent(Python)와의 내부 API라 JSON은 snake_case다. */
public final class AgentClientReqDTO {
  private AgentClientReqDTO() {}

  /** 조건 추출 요청. agent의 {@code PromptUpdate}와 맞춘다. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Plan(
      String prompt,
      @JsonProperty("accommodation_prompt") String accommodationPrompt,
      @JsonProperty("purpose_prompt") String purposePrompt,
      @JsonProperty("origin_hint") String originHint) {}

  /**
   * 조합 추천 요청. 후보의 {@code key}(F1, S1 …)로 고르게 하고, 금액 합계는 core가 다시 계산한다.
   *
   * @param maxOptions 돌려받을 조합 개수 상한.
   */
  public record Recommend(
      Trip trip,
      List<FlightCandidate> flights,
      List<StayCandidate> stays,
      @JsonProperty("max_options") int maxOptions) {}

  /** core가 확정한 여행 조건. 날짜는 {@code YYYY-MM-DD}. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Trip(
      String origin,
      String destination,
      @JsonProperty("departure_date") String departureDate,
      @JsonProperty("return_date") String returnDate,
      int nights,
      int travelers,
      @JsonProperty("budget_krw") Long budgetKrw,
      String purpose,
      @JsonProperty("core_travel_goal") String coreTravelGoal,
      @JsonProperty("desired_satisfaction") String desiredSatisfaction,
      @JsonProperty("accommodation_preferences") List<String> accommodationPreferences,
      @JsonProperty("departure_period") String departurePeriod) {}

  /** 항공 후보. {@code priceKrw}는 전체 인원 왕복 합계. */
  public record FlightCandidate(
      String key,
      String carrier,
      @JsonProperty("price_krw") long priceKrw,
      FlightLeg outbound,
      FlightLeg inbound) {}

  /** 편도 하나. 시각은 공항 현지 시각 {@code YYYY-MM-DDTHH:mm}. */
  public record FlightLeg(
      String from,
      String to,
      @JsonProperty("departing_at") String departingAt,
      @JsonProperty("arriving_at") String arrivingAt,
      @JsonProperty("duration_minutes") Long durationMinutes,
      int stops,
      @JsonProperty("flight_numbers") List<String> flightNumbers) {}

  /** 숙소 후보. {@code priceKrw}는 숙박 전체 기간 합계. 리뷰 점수는 0–10. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record StayCandidate(
      String key,
      String name,
      Integer stars,
      @JsonProperty("review_score") Double reviewScore,
      @JsonProperty("review_count") Integer reviewCount,
      @JsonProperty("price_krw") long priceKrw,
      String room,
      String board,
      boolean refundable,
      String address) {}
}
