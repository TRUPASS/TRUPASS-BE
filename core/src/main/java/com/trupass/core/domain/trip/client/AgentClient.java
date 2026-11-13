package com.trupass.core.domain.trip.client;

import com.trupass.core.domain.trip.client.dto.AgentClientReqDTO;
import com.trupass.core.domain.trip.client.dto.AgentClientResDTO;

/**
 * AI 에이전트({@code agent}, Python) 연동. AI는 느리고 불안정하므로 trip 도메인에서만 부르고, 결제 흐름(checkout)과 분리한다. 연결은
 * {@code global/config/ClientConfig}에서만 한다.
 */
public interface AgentClient {

  /** {@code POST /compose}. 자연어 요청으로 패키지 2~3안을 만든다. */
  AgentClientResDTO.Compose compose(AgentClientReqDTO.Compose request);
}
