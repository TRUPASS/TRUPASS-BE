package com.trupass.core.domain.payment;

import com.trupass.core.domain.common.Money;
import java.time.Instant;

public record PaymentCancellation(
    PaymentId paymentId, Money cancelledAmount, Money remainingAmount, Instant cancelledAt) {}
