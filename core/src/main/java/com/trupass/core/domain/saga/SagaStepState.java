package com.trupass.core.domain.saga;

import com.trupass.core.domain.checkout.MerchantId;
import java.time.Instant;

/**
 * Saga 단계 하나의 상태.
 *
 * @param externalRef 판매자/PG가 돌려준 참조 ID(holdId, paymentKey 등). 아직 없으면 null.
 */
public record SagaStepState(
    MerchantId merchantId,
    SagaStepType type,
    SagaStepStatus status,
    int attempt,
    String externalRef,
    Instant updatedAt) {}
