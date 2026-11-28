package com.trupass.core.domain.search.exception;

import com.trupass.core.global.apiPayload.code.BaseErrorCode;
import org.springframework.http.HttpStatus;

/** 항공·숙소 검색과 추천 조합 에러. */
public enum SearchErrorCode implements BaseErrorCode {
  UNSUPPORTED_DESTINATION(HttpStatus.UNPROCESSABLE_CONTENT, "SEARCH422_1", "아직 지원하지 않는 도시입니다."),
  DESTINATION_REQUIRED(
      HttpStatus.UNPROCESSABLE_CONTENT, "SEARCH422_4", "목적지를 알 수 없습니다. 가고 싶은 도시를 적어 주세요."),
  SAME_CITY(HttpStatus.UNPROCESSABLE_CONTENT, "SEARCH422_5", "출발지와 목적지가 같습니다."),
  TOO_MANY_TRAVELERS(HttpStatus.UNPROCESSABLE_CONTENT, "SEARCH422_6", "한 번에 9명까지 검색할 수 있습니다."),
  DEPARTURE_DATE_REQUIRED(
      HttpStatus.UNPROCESSABLE_CONTENT, "SEARCH422_2", "출발 날짜를 알 수 없습니다. 날짜를 함께 적어 주세요."),
  INVALID_TRIP_DATES(
      HttpStatus.UNPROCESSABLE_CONTENT, "SEARCH422_3", "여행 날짜가 올바르지 않습니다(지난 날짜이거나 돌아오는 날이 더 빠름)."),
  NO_FLIGHT_FOUND(HttpStatus.NOT_FOUND, "SEARCH404_1", "조건에 맞는 항공편이 없습니다."),
  NO_STAY_FOUND(HttpStatus.NOT_FOUND, "SEARCH404_2", "조건에 맞는 숙소가 없습니다."),
  FLIGHT_SEARCH_FAILED(HttpStatus.BAD_GATEWAY, "SEARCH502_1", "항공 검색에 실패했습니다."),
  STAY_SEARCH_FAILED(HttpStatus.BAD_GATEWAY, "SEARCH502_2", "숙소 검색에 실패했습니다."),
  SEARCH_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "SEARCH503_1", "검색 API 키가 설정되지 않았습니다."),
  SEARCH_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "SEARCH504_1", "검색 응답이 늦어지고 있습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;

  SearchErrorCode(HttpStatus status, String code, String message) {
    this.status = status;
    this.code = code;
    this.message = message;
  }

  @Override
  public HttpStatus getStatus() {
    return status;
  }

  @Override
  public String getCode() {
    return code;
  }

  @Override
  public String getMessage() {
    return message;
  }
}
