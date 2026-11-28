package com.trupass.core.domain.search.client.liteapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.trupass.core.domain.search.client.StaySearchClient;
import com.trupass.core.domain.search.client.dto.SearchClientReqDTO;
import com.trupass.core.domain.search.client.dto.SearchClientResDTO;
import com.trupass.core.domain.search.exception.SearchErrorCode;
import com.trupass.core.global.apiPayload.exception.GeneralException;
import com.trupass.core.global.common.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * LiteAPI 샌드박스 숙소 검색({@code POST /hotels/rates}). 호텔 정보({@code hotels[]})와 요금({@code data[]})이 따로
 * 와서 호텔 id로 합친다.
 *
 * <p>"빈 방 없음"은 HTTP 200에 {@code {"error":{"code":2001}}} 본문으로 온다. 상태 코드만 보면 성공으로 착각하므로 본문을 확인한다.
 * 샌드박스 키({@code sand_})만 받는다.
 */
public class LiteApiStaySearchClient implements StaySearchClient {
  public static final String SOURCE = "LITEAPI_SANDBOX";
  private static final String SANDBOX_KEY_PREFIX = "sand_";
  private static final int NO_AVAILABILITY = 2001;
  private static final Logger log = LoggerFactory.getLogger(LiteApiStaySearchClient.class);

  private final RestClient restClient;
  private final boolean configured;

  public LiteApiStaySearchClient(RestClient restClient, String apiKey) {
    this.configured = apiKey != null && !apiKey.isBlank();
    if (configured && !apiKey.startsWith(SANDBOX_KEY_PREFIX)) {
      throw new IllegalStateException(
          "trupass.search.liteapi.api-key must be a sandbox key (sand_…)");
    }
    this.restClient =
        configured ? restClient.mutate().defaultHeader("X-API-Key", apiKey).build() : restClient;
  }

  @Override
  public List<SearchClientResDTO.StayOffer> search(SearchClientReqDTO.Stay request) {
    if (!configured) {
      throw new GeneralException(SearchErrorCode.SEARCH_NOT_CONFIGURED);
    }
    RatesResponse response;
    try {
      response =
          restClient
              .post()
              .uri("/hotels/rates")
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON)
              .body(toBody(request))
              .retrieve()
              .body(RatesResponse.class);
    } catch (RestClientResponseException e) {
      log.warn("liteapi search failed: status={}", e.getStatusCode().value());
      throw new GeneralException(SearchErrorCode.STAY_SEARCH_FAILED, e);
    } catch (ResourceAccessException e) {
      throw new GeneralException(SearchErrorCode.SEARCH_TIMEOUT, e);
    } catch (RestClientException e) {
      throw new GeneralException(SearchErrorCode.STAY_SEARCH_FAILED, e);
    }
    return toOffers(response);
  }

  private static RatesRequest toBody(SearchClientReqDTO.Stay request) {
    return new RatesRequest(
        request.cityName(),
        request.countryCode(),
        request.checkin().toString(),
        request.checkout().toString(),
        List.of(new Occupancy(request.adults())),
        Money.KRW,
        "KR",
        request.limit(),
        1,
        request.minReviewScore(),
        true);
  }

  static List<SearchClientResDTO.StayOffer> toOffers(RatesResponse response) {
    if (response == null) {
      throw new GeneralException(SearchErrorCode.STAY_SEARCH_FAILED);
    }
    if (response.error() != null) {
      if (Objects.equals(response.error().code(), NO_AVAILABILITY)) {
        return List.of();
      }
      log.warn("liteapi search error: code={}", response.error().code());
      throw new GeneralException(SearchErrorCode.STAY_SEARCH_FAILED);
    }
    if (response.data() == null) {
      return List.of();
    }
    Map<String, Hotel> hotels =
        response.hotels() == null
            ? Map.of()
            : response.hotels().stream()
                .filter(hotel -> hotel.id() != null)
                .collect(Collectors.toMap(Hotel::id, Function.identity(), (a, b) -> a));

    List<SearchClientResDTO.StayOffer> result = new ArrayList<>();
    int skipped = 0;
    for (HotelRates rates : response.data()) {
      Hotel hotel = hotels.get(rates.hotelId());
      if (hotel == null || rates.roomTypes() == null || rates.roomTypes().isEmpty()) {
        skipped++;
        continue;
      }
      try {
        result.add(toOffer(hotel, cheapest(rates.roomTypes())));
      } catch (ArithmeticException | IllegalArgumentException | NullPointerException e) {
        skipped++;
      }
    }
    if (skipped > 0) {
      log.warn("liteapi rates skipped: {} of {}", skipped, response.data().size());
    }
    return result;
  }

  private static RoomType cheapest(List<RoomType> roomTypes) {
    return roomTypes.stream()
        .filter(room -> room.offerRetailRate() != null && room.offerRetailRate().amount() != null)
        .min(Comparator.comparing(room -> room.offerRetailRate().amount()))
        .orElseThrow(() -> new IllegalArgumentException("no priced room"));
  }

  private static SearchClientResDTO.StayOffer toOffer(Hotel hotel, RoomType room) {
    Money price =
        Money.fromDecimal(room.offerRetailRate().amount(), room.offerRetailRate().currency());
    Rate rate = room.rates() == null || room.rates().isEmpty() ? null : room.rates().getFirst();
    boolean refundable =
        rate != null
            && rate.cancellationPolicies() != null
            && "RFN".equals(rate.cancellationPolicies().refundableTag());
    boolean taxesIncluded =
        rate == null
            || rate.retailRate() == null
            || rate.retailRate().taxesAndFees() == null
            || rate.retailRate().taxesAndFees().stream()
                .allMatch(tax -> !Boolean.FALSE.equals(tax.included()));
    return new SearchClientResDTO.StayOffer(
        SOURCE,
        hotel.id(),
        room.offerId(),
        hotel.name(),
        hotel.stars(),
        hotel.rating(),
        hotel.reviewCount(),
        hotel.address(),
        hotel.latitude(),
        hotel.longitude(),
        rate == null ? null : rate.name(),
        rate == null ? null : rate.boardType(),
        rate == null ? null : rate.boardName(),
        refundable,
        taxesIncluded,
        price);
  }

  // ---- LiteAPI 요청 형식 ----

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record RatesRequest(
      String cityName,
      String countryCode,
      String checkin,
      String checkout,
      List<Occupancy> occupancies,
      String currency,
      String guestNationality,
      int limit,
      int maxRatesPerHotel,
      Double minRating,
      boolean includeHotelData) {}

  record Occupancy(int adults) {}

  // ---- LiteAPI 응답 형식(필요한 필드만) ----

  @JsonIgnoreProperties(ignoreUnknown = true)
  record RatesResponse(List<HotelRates> data, List<Hotel> hotels, ApiError error) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ApiError(Integer code, String message) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record HotelRates(String hotelId, List<RoomType> roomTypes) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record RoomType(String offerId, Amount offerRetailRate, List<Rate> rates) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Amount(BigDecimal amount, String currency) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Rate(
      String name,
      String boardType,
      String boardName,
      CancellationPolicies cancellationPolicies,
      RetailRate retailRate) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record CancellationPolicies(String refundableTag) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record RetailRate(List<TaxOrFee> taxesAndFees) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record TaxOrFee(Boolean included) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Hotel(
      String id,
      String name,
      String address,
      Double latitude,
      Double longitude,
      Double rating,
      Integer stars,
      @JsonProperty("review_count") Integer reviewCount) {}
}
