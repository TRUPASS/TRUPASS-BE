package com.trupass.core.domain.merchant;

import com.trupass.core.domain.common.Money;
import java.time.Instant;

/** Trupass → 판매자 정산 결과. */
public record MerchantPayment(HoldId holdId, Money amount, Instant paidAt) {}
