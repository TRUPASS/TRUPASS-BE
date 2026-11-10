package com.trupass.core.domain.payment;

import java.time.Instant;

public record PaymentEvent(PaymentId paymentId, PaymentEventType type, Instant occurredAt) {}
