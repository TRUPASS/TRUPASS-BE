package com.trupass.core.domain.pkg;

import com.trupass.core.domain.checkout.MerchantId;
import com.trupass.core.domain.common.Money;
import java.time.Instant;

/**
 * 판매자가 제시한 상품 1건. 금액은 반드시 오퍼 ID와 함께 다닌다.
 *
 * @param expiresAt 오퍼 유효 기한. 없으면 null.
 */
public record Offer(
    OfferId offerId,
    MerchantId merchantId,
    OfferCategory category,
    String title,
    Money price,
    Instant expiresAt) {}
