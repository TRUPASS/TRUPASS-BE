package com.trupass.core.domain.payment;

import com.trupass.core.domain.checkout.CheckoutId;
import com.trupass.core.domain.checkout.IdempotencyKey;
import com.trupass.core.domain.common.Money;

/**
 * 사용자 → Trupass 결제 승인 요청. 체크아웃당 1건이다.
 *
 * @param paymentToken FE 결제창이 돌려준 PG 측 인증 토큰.
 */
public record CollectRequest(
    CheckoutId checkoutId, Money amount, String paymentToken, IdempotencyKey idempotencyKey) {}
