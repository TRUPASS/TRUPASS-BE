package com.trupass.core.domain.trip.client.http;

import com.trupass.core.domain.trip.client.AgentClient;
import com.trupass.core.domain.trip.client.dto.AgentClientReqDTO;
import com.trupass.core.domain.trip.client.dto.AgentClientResDTO;
import com.trupass.core.domain.trip.exception.TripErrorCode;
import com.trupass.core.global.apiPayload.exception.GeneralException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * agent 서버({@code trupass.services.agent-base-url})를 HTTP로 부르는 구현체. agent는 {@code
 * X-Internal-API-Key} 헤더가 없거나 틀리면 401, 서버에 키가 없으면 503을 준다.
 */
public class HttpAgentClient implements AgentClient {
  static final String INTERNAL_KEY_HEADER = "X-Internal-API-Key";
  private static final Logger log = LoggerFactory.getLogger(HttpAgentClient.class);

  private final RestClient restClient;

  public HttpAgentClient(RestClient restClient, String internalApiKey) {
    if (internalApiKey == null || internalApiKey.isBlank()) {
      log.warn("trupass.services.agent-internal-api-key is empty; agent calls will be rejected");
      this.restClient = restClient;
    } else {
      this.restClient =
          restClient.mutate().defaultHeader(INTERNAL_KEY_HEADER, internalApiKey).build();
    }
  }

  @Override
  public AgentClientResDTO.Plan plan(AgentClientReqDTO.Plan request) {
    AgentClientResDTO.Plan response =
        post("/api/v1/trips/plan", request, AgentClientResDTO.Plan.class);
    if (response.requirements() == null) {
      throw new GeneralException(TripErrorCode.INVALID_PACKAGE);
    }
    return response;
  }

  @Override
  public AgentClientResDTO.Recommend recommend(AgentClientReqDTO.Recommend request) {
    AgentClientResDTO.Recommend response =
        post("/api/v1/trips/recommend", request, AgentClientResDTO.Recommend.class);
    if (response.options() == null || response.options().isEmpty()) {
      throw new GeneralException(TripErrorCode.INVALID_PACKAGE);
    }
    return response;
  }

  private <T> T post(String path, Object body, Class<T> type) {
    try {
      T response = restClient.post().uri(path).body(body).retrieve().body(type);
      if (response == null) {
        throw new GeneralException(TripErrorCode.INVALID_PACKAGE);
      }
      return response;
    } catch (RestClientResponseException e) {
      log.warn(
          "agent call failed: path={} status={} body={}",
          path,
          e.getStatusCode().value(),
          abbreviate(e.getResponseBodyAsString()));
      throw new GeneralException(TripErrorCode.AGENT_UNAVAILABLE, e);
    } catch (ResourceAccessException e) {
      throw new GeneralException(TripErrorCode.AGENT_TIMEOUT, e);
    } catch (RestClientException e) {
      throw new GeneralException(TripErrorCode.AGENT_UNAVAILABLE, e);
    }
  }

  private static String abbreviate(String body) {
    if (body == null) {
      return "";
    }
    return body.length() <= 300 ? body : body.substring(0, 300) + "…";
  }
}
