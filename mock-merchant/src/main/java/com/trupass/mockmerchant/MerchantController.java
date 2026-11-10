package com.trupass.mockmerchant;

import com.trupass.mockmerchant.model.BookingResponse;
import com.trupass.mockmerchant.model.CreateHoldRequest;
import com.trupass.mockmerchant.model.HoldResponse;
import com.trupass.mockmerchant.model.Merchant;
import com.trupass.mockmerchant.model.Offer;
import com.trupass.mockmerchant.model.PayHoldRequest;
import jakarta.validation.Valid;
import java.util.Collection;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mock 판매자 API. 모든 엔드포인트는 {@link FailureInjectionFilter}의 실패 주입과 {@link IdempotencyFilter}의 멱등 처리를
 * 거친다.
 */
@RestController
public class MerchantController {
  private final MerchantStore store;

  public MerchantController(MerchantStore store) {
    this.store = store;
  }

  @GetMapping("/merchants")
  public Collection<Merchant> merchants() {
    return store.merchants();
  }

  @GetMapping("/merchants/{merchantId}/offers")
  public List<Offer> offers(@PathVariable String merchantId) {
    return store.offersOf(merchantId);
  }

  @PostMapping("/merchants/{merchantId}/holds")
  @ResponseStatus(HttpStatus.CREATED)
  public HoldResponse createHold(
      @PathVariable String merchantId, @Valid @RequestBody CreateHoldRequest request) {
    return store.createHold(merchantId, request);
  }

  @PostMapping("/holds/{holdId}/release")
  public HoldResponse release(@PathVariable String holdId) {
    return store.release(holdId);
  }

  @PostMapping("/holds/{holdId}/pay")
  public HoldResponse pay(@PathVariable String holdId, @Valid @RequestBody PayHoldRequest request) {
    return store.pay(holdId, request);
  }

  @PostMapping("/holds/{holdId}/confirm")
  @ResponseStatus(HttpStatus.CREATED)
  public BookingResponse confirm(@PathVariable String holdId) {
    return store.confirm(holdId);
  }

  @PostMapping("/bookings/{bookingId}/cancel")
  public BookingResponse cancel(@PathVariable String bookingId) {
    return store.cancelBooking(bookingId);
  }
}
