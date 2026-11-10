package com.trupass.core.domain.merchant;

import com.trupass.core.domain.checkout.MerchantId;
import java.time.Instant;

public record Booking(
    BookingId bookingId, HoldId holdId, MerchantId merchantId, Instant confirmedAt) {}
