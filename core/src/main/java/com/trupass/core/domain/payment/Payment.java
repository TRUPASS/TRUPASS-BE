package com.trupass.core.domain.payment;

import com.trupass.core.domain.checkout.CheckoutId;
import com.trupass.core.domain.common.Money;
import java.time.Instant;

public record Payment(
    PaymentId paymentId, CheckoutId checkoutId, Money amount, Instant approvedAt) {}
