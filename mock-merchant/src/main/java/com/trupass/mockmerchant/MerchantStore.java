package com.trupass.mockmerchant;

import com.trupass.mockmerchant.model.BookingResponse;
import com.trupass.mockmerchant.model.BookingStatus;
import com.trupass.mockmerchant.model.Category;
import com.trupass.mockmerchant.model.CreateHoldRequest;
import com.trupass.mockmerchant.model.HoldResponse;
import com.trupass.mockmerchant.model.HoldStatus;
import com.trupass.mockmerchant.model.Merchant;
import com.trupass.mockmerchant.model.Offer;
import com.trupass.mockmerchant.model.PayHoldRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 인메모리 판매자 상태. 재시작하면 사라진다.
 *
 * <p>hold 상태 전이: HELD → (RELEASED | EXPIRED | PAID), PAID → CONFIRMED. 보상 경로 테스트를 위해
 * release·cancel은 같은 대상을 여러 번 불러도 성공한다.
 */
@Component
public class MerchantStore {
  private final Clock clock;
  private final MockMerchantProperties properties;

  private final Map<String, Merchant> merchants =
      toMap(
          List.of(
              new Merchant("A", "Mock Air", Category.FLIGHT),
              new Merchant("B", "Mock Stay", Category.STAY),
              new Merchant("C", "Mock Experience", Category.EXPERIENCE)),
          Merchant::merchantId);

  private final Map<String, Offer> offers =
      toMap(
          List.of(
              new Offer(
                  "A-ICN-KIX-001", "A", Category.FLIGHT, "인천 → 오사카 왕복 (이코노미)", 320_000, "KRW"),
              new Offer(
                  "A-ICN-FUK-001", "A", Category.FLIGHT, "인천 → 후쿠오카 왕복 (이코노미)", 250_000, "KRW"),
              new Offer("B-OSA-HOTEL-001", "B", Category.STAY, "오사카 난바 호텔 2박", 280_000, "KRW"),
              new Offer("B-FUK-HOTEL-001", "B", Category.STAY, "후쿠오카 하카타 호텔 2박", 220_000, "KRW"),
              new Offer(
                  "C-OSA-USJ-001", "C", Category.EXPERIENCE, "유니버설 스튜디오 재팬 1일권", 110_000, "KRW"),
              new Offer(
                  "C-FUK-FOOD-001", "C", Category.EXPERIENCE, "후쿠오카 야타이 푸드 투어", 60_000, "KRW")),
          Offer::offerId);

  private final Map<String, HoldResponse> holds = new ConcurrentHashMap<>();
  private final Map<String, BookingResponse> bookings = new ConcurrentHashMap<>();

  public MerchantStore(Clock clock, MockMerchantProperties properties) {
    this.clock = clock;
    this.properties = properties;
  }

  public Collection<Merchant> merchants() {
    return merchants.values();
  }

  public List<Offer> offersOf(String merchantId) {
    requireMerchant(merchantId);
    return offers.values().stream().filter(o -> o.merchantId().equals(merchantId)).toList();
  }

  public HoldResponse createHold(String merchantId, CreateHoldRequest request) {
    requireMerchant(merchantId);
    Offer offer = offers.get(request.offerId());
    if (offer == null || !offer.merchantId().equals(merchantId)) {
      throw new MockMerchantException(
          HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND", "offer " + request.offerId() + " not found");
    }
    HoldResponse hold =
        new HoldResponse(
            "hold_" + UUID.randomUUID(),
            merchantId,
            offer.offerId(),
            request.quantity(),
            offer.amount() * request.quantity(),
            offer.currency(),
            HoldStatus.HELD,
            now().plus(properties.holdTtl()));
    holds.put(hold.holdId(), hold);
    return hold;
  }

  public HoldResponse release(String holdId) {
    return transition(
        holdId,
        hold ->
            switch (hold.status()) {
              case HELD, RELEASED, EXPIRED -> hold.withStatus(HoldStatus.RELEASED);
              default -> throw conflict("HOLD_NOT_RELEASABLE", hold);
            });
  }

  public HoldResponse pay(String holdId, PayHoldRequest request) {
    return transition(
        holdId,
        hold ->
            switch (hold.status()) {
              case HELD -> {
                if (request.amount() != hold.amount()
                    || !request.currency().equals(hold.currency())) {
                  throw new MockMerchantException(
                      HttpStatus.UNPROCESSABLE_CONTENT,
                      "AMOUNT_MISMATCH",
                      "expected " + hold.amount() + " " + hold.currency());
                }
                yield hold.withStatus(HoldStatus.PAID);
              }
              case EXPIRED ->
                  throw new MockMerchantException(
                      HttpStatus.GONE, "HOLD_EXPIRED", "hold " + hold.holdId() + " expired");
              default -> throw conflict("HOLD_NOT_PAYABLE", hold);
            });
  }

  public BookingResponse confirm(String holdId) {
    HoldResponse hold =
        transition(
            holdId,
            h -> {
              if (h.status() != HoldStatus.PAID) {
                throw conflict("HOLD_NOT_CONFIRMABLE", h);
              }
              return h.withStatus(HoldStatus.CONFIRMED);
            });
    BookingResponse booking =
        new BookingResponse(
            "bk_" + UUID.randomUUID(),
            hold.holdId(),
            hold.merchantId(),
            BookingStatus.CONFIRMED,
            hold.amount(),
            hold.currency(),
            now());
    bookings.put(booking.bookingId(), booking);
    return booking;
  }

  public BookingResponse cancelBooking(String bookingId) {
    return bookings.compute(
        bookingId,
        (id, booking) -> {
          if (booking == null) {
            throw new MockMerchantException(
                HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "booking " + id + " not found");
          }
          return booking.cancelled(now());
        });
  }

  private HoldResponse transition(String holdId, UnaryOperator<HoldResponse> change) {
    return holds.compute(
        holdId,
        (id, hold) -> {
          if (hold == null) {
            throw new MockMerchantException(
                HttpStatus.NOT_FOUND, "HOLD_NOT_FOUND", "hold " + id + " not found");
          }
          return change.apply(expireIfNeeded(hold));
        });
  }

  private HoldResponse expireIfNeeded(HoldResponse hold) {
    if (hold.status() == HoldStatus.HELD && !now().isBefore(hold.expiresAt())) {
      return hold.withStatus(HoldStatus.EXPIRED);
    }
    return hold;
  }

  private void requireMerchant(String merchantId) {
    if (!merchants.containsKey(merchantId)) {
      throw new MockMerchantException(
          HttpStatus.NOT_FOUND, "MERCHANT_NOT_FOUND", "merchant " + merchantId + " not found");
    }
  }

  private static MockMerchantException conflict(String code, HoldResponse hold) {
    return new MockMerchantException(
        HttpStatus.CONFLICT, code, "hold " + hold.holdId() + " is " + hold.status());
  }

  private Instant now() {
    return clock.instant();
  }

  private static <T> Map<String, T> toMap(
      List<T> items, java.util.function.Function<T, String> key) {
    return items.stream().collect(Collectors.toMap(key, t -> t, (a, b) -> a, LinkedHashMap::new));
  }
}
