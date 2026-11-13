package com.trupass.core.domain.trip.client.dto;

/** {@code AgentClient}에 넘기는 요청 형식. JSON은 camelCase로 agent의 {@code ComposeRequest}와 맞춘다. */
public final class AgentClientReqDTO {
  private AgentClientReqDTO() {}

  /**
   * 패키지 조합 요청.
   *
   * @param tripId core가 발급한 여행 요청 ID. agent가 core 도구를 부를 때 그대로 넘긴다.
   * @param query 사용자가 입력한 자연어 요청.
   */
  public record Compose(String tripId, String query) {}
}
