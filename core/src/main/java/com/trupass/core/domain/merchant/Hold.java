package com.trupass.core.domain.merchant;

import com.trupass.core.domain.checkout.MerchantId;
import com.trupass.core.domain.common.Money;
import com.trupass.core.domain.pkg.OfferId;
import java.time.Instant;

/** 재고만 잡은 상태. 돈은 아직 움직이지 않았다. {@code expiresAt}이 지나면 판매자가 자동 해제한다. */
public record Hold(
    HoldId holdId, MerchantId merchantId, OfferId offerId, Money amount, Instant expiresAt) {}
