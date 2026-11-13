package com.trupass.core.domain.trip.client.http;

import com.trupass.core.domain.trip.client.AgentClient;
import com.trupass.core.domain.trip.client.dto.AgentClientReqDTO;
import com.trupass.core.domain.trip.client.dto.AgentClientResDTO;
import com.trupass.core.domain.trip.exception.TripErrorCode;
import com.trupass.core.global.apiPayload.exception.GeneralException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** agent 서버({@code trupass.services.agent-base-url})를 HTTP로 부르는 구현체. */
public class HttpAgentClient implements AgentClient {
  private final RestClient restClient;

  public HttpAgentClient(RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public AgentClientResDTO.Compose compose(AgentClientReqDTO.Compose request) {
    try {
      AgentClientResDTO.Compose response =
          restClient
              .post()
              .uri("/compose")
              .body(request)
              .retrieve()
              .body(AgentClientResDTO.Compose.class);
      if (response == null) {
        throw new GeneralException(TripErrorCode.INVALID_PACKAGE);
      }
      return response;
    } catch (ResourceAccessException e) {
      throw new GeneralException(TripErrorCode.AGENT_TIMEOUT, e);
    } catch (RestClientException e) {
      throw new GeneralException(TripErrorCode.AGENT_UNAVAILABLE, e);
    }
  }
}
