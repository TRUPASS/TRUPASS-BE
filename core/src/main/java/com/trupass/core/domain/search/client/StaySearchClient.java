package com.trupass.core.domain.search.client;

import com.trupass.core.domain.search.client.dto.SearchClientReqDTO;
import com.trupass.core.domain.search.client.dto.SearchClientResDTO;
import java.util.List;

/** 숙소 검색. 구현체는 {@code trupass.clients.stay-search} 값으로 고른다. */
public interface StaySearchClient {

  /** 숙소 offer 목록(호텔당 가장 싼 객실 하나). 빈 방이 없으면 빈 목록이다. */
  List<SearchClientResDTO.StayOffer> search(SearchClientReqDTO.Stay request);
}
