package com.trupass.core.domain.search.client;

import com.trupass.core.domain.search.client.dto.SearchClientReqDTO;
import com.trupass.core.domain.search.client.dto.SearchClientResDTO;
import java.util.List;

/**
 * 항공 검색. 소스(Duffel 테스트 모드 등)와 상관없는 offer 모델로 돌려준다. 구현체는 {@code global/config/ClientConfig}에서 {@code
 * trupass.clients.flight-search} 값으로 고른다.
 */
public interface FlightSearchClient {

  /** 왕복 항공 offer 목록. 결과가 없으면 빈 목록이다. 외부 API가 실패하면 {@code GeneralException}을 던진다. */
  List<SearchClientResDTO.FlightOffer> search(SearchClientReqDTO.Flight request);
}
