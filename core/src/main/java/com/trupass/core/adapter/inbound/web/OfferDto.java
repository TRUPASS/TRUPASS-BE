package com.trupass.core.adapter.inbound.web;

import com.trupass.core.domain.pkg.OfferCategory;
import java.time.Instant;

/** agent의 {@code OfferRef} 스키마와 필드를 맞춘다(JSON은 camelCase). */
public record OfferDto(
    String offerId,
    String merchantId,
    OfferCategory category,
    String title,
    long amount,
    String currency,
    Instant expiresAt) {}
