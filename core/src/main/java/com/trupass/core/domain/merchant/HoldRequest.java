package com.trupass.core.domain.merchant;

import com.trupass.core.domain.checkout.CheckoutId;
import com.trupass.core.domain.checkout.IdempotencyKey;
import com.trupass.core.domain.checkout.MerchantId;
import com.trupass.core.domain.pkg.OfferId;

public record HoldRequest(
    CheckoutId checkoutId,
    MerchantId merchantId,
    OfferId offerId,
    int quantity,
    IdempotencyKey idempotencyKey) {}
