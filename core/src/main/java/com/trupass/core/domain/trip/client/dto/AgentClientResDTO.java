package com.trupass.core.domain.trip.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code AgentClient}가 돌려받는 응답 형식. agent의 pydantic 모델과 맞춘다. agent는 값이 없는 필드를 빼고 보내므로 모든 필드가 {@code
 * null}일 수 있다.
 */
public final class AgentClientResDTO {
  private AgentClientResDTO() {}

  /** {@code PlanResponse}. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Plan(
      @JsonProperty("prompt_id") String promptId,
      Requirements requirements,
      @JsonProperty("gemini_called") Boolean geminiCalled,
      @JsonProperty("prompt_tokens") Integer promptTokens,
      @JsonProperty("output_tokens") Integer outputTokens) {}

  /**
   * {@code TripRequirements}. 도시는 텍스트("오사카"), 평점은 1–5 척도다.
   *
   * @param departurePeriod {@code morning} | {@code afternoon} | {@code evening}
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Requirements(
      String origin,
      String destination,
      @JsonProperty("departure_date") LocalDate departureDate,
      @JsonProperty("return_date") LocalDate returnDate,
      Integer travelers,
      @JsonProperty("budget_krw") Long budgetKrw,
      String purpose,
      @JsonProperty("core_travel_goal") String coreTravelGoal,
      @JsonProperty("desired_satisfaction") String desiredSatisfaction,
      @JsonProperty("min_traveler_rating") Double minTravelerRating,
      @JsonProperty("accommodation_preferences") List<String> accommodationPreferences,
      @JsonProperty("transport_preferences") List<String> transportPreferences,
      @JsonProperty("departure_period") String departurePeriod,
      @JsonProperty("additional_cities") List<String> additionalCities) {}

  /** {@code RecommendResponse}. 금액은 들어 있지 않다. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Recommend(
      String summary,
      List<Option> options,
      @JsonProperty("gemini_called") Boolean geminiCalled,
      @JsonProperty("prompt_tokens") Integer promptTokens,
      @JsonProperty("output_tokens") Integer outputTokens) {}

  /** 조합 하나. 후보 key로만 가리킨다. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Option(
      @JsonProperty("flight_key") String flightKey,
      @JsonProperty("stay_key") String stayKey,
      String title,
      String reason) {}
}
