package com.trupass.core.domain.merchant;

import com.trupass.core.domain.checkout.IdempotencyKey;
import com.trupass.core.domain.checkout.MerchantId;
import com.trupass.core.domain.common.Money;

public record MerchantPayRequest(
    MerchantId merchantId, HoldId holdId, Money amount, IdempotencyKey idempotencyKey) {}
