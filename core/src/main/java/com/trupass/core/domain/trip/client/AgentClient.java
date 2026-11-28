package com.trupass.core.domain.trip.client;

import com.trupass.core.domain.trip.client.dto.AgentClientReqDTO;
import com.trupass.core.domain.trip.client.dto.AgentClientResDTO;

/**
 * AI 에이전트({@code agent}, Python) 연동. AI는 느리고 불안정하므로 결제 흐름(checkout)과 분리한다. 연결은 {@code
 * global/config/ClientConfig}에서만 한다.
 *
 * <p>AI는 조건을 뽑고(plan) 후보 중에서 고르기만 한다(recommend). 상품 검색과 금액 계산은 core가 한다.
 */
public interface AgentClient {

  /** {@code POST /api/v1/trips/plan}. 사용자가 쓴 문장 3개에서 검색 조건을 뽑는다. */
  AgentClientResDTO.Plan plan(AgentClientReqDTO.Plan request);

  /** {@code POST /api/v1/trips/recommend}. core가 고른 항공·숙소 후보 중에서 조합 1~3개를 고른다. */
  AgentClientResDTO.Recommend recommend(AgentClientReqDTO.Recommend request);
}
