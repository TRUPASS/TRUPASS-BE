package com.trupass.core.domain.search.client.duffel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.trupass.core.domain.search.client.FlightSearchClient;
import com.trupass.core.domain.search.client.dto.SearchClientReqDTO;
import com.trupass.core.domain.search.client.dto.SearchClientResDTO;
import com.trupass.core.domain.search.exception.SearchErrorCode;
import com.trupass.core.global.apiPayload.exception.GeneralException;
import com.trupass.core.global.common.Money;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Duffel 테스트 모드 항공 검색. 두 번 부른다.
 *
 * <ol>
 *   <li>{@code POST /air/offer_requests?return_offers=false}: 검색을 만들고 id만 받는다.
 *   <li>{@code GET /air/offers?offer_request_id=…&sort=total_amount}: 싼 순서로 offer 한 페이지(최대 100개)를
 *       받는다.
 * </ol>
 *
 * <p>한 번에 offer까지 받으면 응답이 수 MB이고 정렬도 안 돼 있어서 나눴다. 주문(order)은 만들지 않는다. 테스트 토큰({@code duffel_test_})만
 * 받고, 다른 토큰이면 애플리케이션이 뜨지 않는다. live 토큰은 실제 돈이 나간다.
 */
public class DuffelFlightSearchClient implements FlightSearchClient {
  public static final String SOURCE = "DUFFEL_TEST";
  private static final String TEST_TOKEN_PREFIX = "duffel_test_";
  private static final int SUPPLIER_TIMEOUT_MS = 15_000;
  private static final int PAGE_SIZE = 100;
  private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
  private static final Logger log = LoggerFactory.getLogger(DuffelFlightSearchClient.class);

  private final RestClient restClient;
  private final boolean configured;

  public DuffelFlightSearchClient(RestClient restClient, String accessToken) {
    this.configured = accessToken != null && !accessToken.isBlank();
    if (configured && !accessToken.startsWith(TEST_TOKEN_PREFIX)) {
      throw new IllegalStateException(
          "trupass.search.duffel.access-token must be a test-mode token (duffel_test_…)");
    }
    this.restClient =
        configured
            ? restClient
                .mutate()
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .defaultHeader("Duffel-Version", "v2")
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build()
            : restClient;
  }

  @Override
  public List<SearchClientResDTO.FlightOffer> search(SearchClientReqDTO.Flight request) {
    if (!configured) {
      throw new GeneralException(SearchErrorCode.SEARCH_NOT_CONFIGURED);
    }
    try {
      String offerRequestId = createOfferRequest(request);
      OffersPage page =
          restClient
              .get()
              .uri(uri ->
                  uri.path("/air/offers")
                      .queryParam("offer_request_id", offerRequestId)
                      .queryParam("sort", "total_amount")
                      .queryParam("limit", PAGE_SIZE)
                      .build())
              .retrieve()
              .body(OffersPage.class);
      if (page == null || page.data() == null) {
        return List.of();
      }
      return toOffers(page.data());
    } catch (RestClientResponseException e) {
      log.warn(
          "duffel search failed: status={} body={}",
          e.getStatusCode().value(),
          abbreviate(e.getResponseBodyAsString()));
      throw new GeneralException(SearchErrorCode.FLIGHT_SEARCH_FAILED, e);
    } catch (ResourceAccessException e) {
      throw new GeneralException(SearchErrorCode.SEARCH_TIMEOUT, e);
    } catch (RestClientException e) {
      throw new GeneralException(SearchErrorCode.FLIGHT_SEARCH_FAILED, e);
    }
  }

  private String createOfferRequest(SearchClientReqDTO.Flight request) {
    DepartureTime window =
        request.departureTimeFrom() == null || request.departureTimeTo() == null
            ? null
            : new DepartureTime(
                format(request.departureTimeFrom()), format(request.departureTimeTo()));
    List<SliceRequest> slices =
        List.of(
            new SliceRequest(
                request.originCityCode(),
                request.destinationCityCode(),
                request.departureDate().toString(),
                window),
            new SliceRequest(
                request.destinationCityCode(),
                request.originCityCode(),
                request.returnDate().toString(),
                null));
    List<PassengerRequest> passengers =
        Collections.nCopies(request.adults(), new PassengerRequest("adult"));
    OfferRequestEnvelope created =
        restClient
            .post()
            .uri(uri ->
                uri.path("/air/offer_requests")
                    .queryParam("return_offers", false)
                    .queryParam("supplier_timeout", SUPPLIER_TIMEOUT_MS)
                    .build())
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                new OfferRequestBody(
                    new OfferRequestData(slices, passengers, request.maxConnections(), "economy")))
            .retrieve()
            .body(OfferRequestEnvelope.class);
    if (created == null || created.data() == null || created.data().id() == null) {
      throw new GeneralException(SearchErrorCode.FLIGHT_SEARCH_FAILED);
    }
    return created.data().id();
  }

  /** 금액·시각을 해석하지 못한 offer는 버리고 로그만 남긴다. 금액을 짐작해서 채우지 않는다. */
  static List<SearchClientResDTO.FlightOffer> toOffers(List<Offer> offers) {
    List<SearchClientResDTO.FlightOffer> result = new ArrayList<>();
    int skipped = 0;
    for (Offer offer : offers) {
      try {
        result.add(toOffer(offer));
      } catch (ArithmeticException
          | DateTimeParseException
          | IllegalArgumentException
          | NullPointerException e) {
        skipped++;
      }
    }
    if (skipped > 0) {
      log.warn("duffel offers skipped: {} of {} could not be mapped", skipped, offers.size());
    }
    return result;
  }

  private static SearchClientResDTO.FlightOffer toOffer(Offer offer) {
    Money price = Money.fromDecimal(new BigDecimal(offer.totalAmount()), offer.totalCurrency());
    List<SearchClientResDTO.FlightSlice> slices =
        offer.slices().stream().map(DuffelFlightSearchClient::toSlice).toList();
    return new SearchClientResDTO.FlightOffer(
        SOURCE,
        offer.id(),
        offer.owner().iataCode(),
        offer.owner().name(),
        price,
        offer.expiresAt() == null ? null : Instant.parse(offer.expiresAt()),
        slices);
  }

  private static SearchClientResDTO.FlightSlice toSlice(Slice slice) {
    List<SearchClientResDTO.FlightSegment> segments =
        slice.segments().stream()
            .map(segment ->
                new SearchClientResDTO.FlightSegment(
                    segment.marketingCarrier().iataCode()
                        + stripLeadingZeros(segment.marketingCarrierFlightNumber()),
                    segment.marketingCarrier().name(),
                    segment.origin().iataCode(),
                    segment.destination().iataCode(),
                    LocalDateTime.parse(segment.departingAt()),
                    LocalDateTime.parse(segment.arrivingAt())))
            .toList();
    return new SearchClientResDTO.FlightSlice(
        slice.origin().iataCode(),
        slice.destination().iataCode(),
        segments.getFirst().departingAt(),
        segments.getLast().arrivingAt(),
        slice.duration() == null ? null : Duration.parse(slice.duration()),
        segments);
  }

  /** {@code "0862"} → {@code "862"}. 전부 0이면 {@code "0"}. */
  static String stripLeadingZeros(String flightNumber) {
    String stripped = flightNumber.replaceFirst("^0+", "");
    return stripped.isEmpty() ? "0" : stripped;
  }

  private static String format(LocalTime time) {
    return time.format(HH_MM);
  }

  private static String abbreviate(String body) {
    if (body == null) {
      return "";
    }
    return body.length() <= 500 ? body : body.substring(0, 500) + "…";
  }

  // ---- Duffel 요청 형식 ----

  record OfferRequestBody(OfferRequestData data) {}

  record OfferRequestData(
      List<SliceRequest> slices,
      List<PassengerRequest> passengers,
      @JsonProperty("max_connections") int maxConnections,
      @JsonProperty("cabin_class") String cabinClass) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record SliceRequest(
      String origin,
      String destination,
      @JsonProperty("departure_date") String departureDate,
      @JsonProperty("departure_time") DepartureTime departureTime) {}

  record DepartureTime(String from, String to) {}

  record PassengerRequest(String type) {}

  // ---- Duffel 응답 형식(필요한 필드만) ----

  @JsonIgnoreProperties(ignoreUnknown = true)
  record OfferRequestEnvelope(OfferRequestCreated data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record OfferRequestCreated(String id) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record OffersPage(List<Offer> data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Offer(
      String id,
      @JsonProperty("total_amount") String totalAmount,
      @JsonProperty("total_currency") String totalCurrency,
      @JsonProperty("expires_at") String expiresAt,
      Airline owner,
      List<Slice> slices) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Airline(@JsonProperty("iata_code") String iataCode, String name) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Slice(String duration, Place origin, Place destination, List<Segment> segments) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Place(@JsonProperty("iata_code") String iataCode) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Segment(
      @JsonProperty("departing_at") String departingAt,
      @JsonProperty("arriving_at") String arrivingAt,
      @JsonProperty("marketing_carrier") Airline marketingCarrier,
      @JsonProperty("marketing_carrier_flight_number") String marketingCarrierFlightNumber,
      Place origin,
      Place destination) {}
}
