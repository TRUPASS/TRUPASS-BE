package com.trupass.core.domain.trip.service;

import com.trupass.core.domain.trip.client.AgentClient;
import com.trupass.core.domain.trip.client.dto.AgentClientReqDTO;
import com.trupass.core.domain.trip.client.dto.AgentClientResDTO;
import org.springframework.stereotype.Service;

/**
 * 여행 요청 → AI에게 패키지 조합 요청 → 패키지 후보 저장. 여행 요청·패키지 후보 엔티티는 테이블 설계 담당자가 추가한다.
 *
 * <p>DB 트랜잭션 안에서 agent를 부르지 않는다. 순서는 "요청 저장 → agent 호출 → 후보 저장"이다.
 */
@Service
public class TripService {
  private final AgentClient agentClient;

  public TripService(AgentClient agentClient) {
    this.agentClient = agentClient;
  }

  public AgentClientResDTO.Compose composePackages(String tripId, String query) {
    return agentClient.compose(new AgentClientReqDTO.Compose(tripId, query));
  }
}
