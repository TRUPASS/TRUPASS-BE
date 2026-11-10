package com.trupass.core.domain.saga

import com.trupass.core.domain.checkout.CheckoutId
import com.trupass.core.domain.checkout.MerchantId
import java.time.Instant

/**
 * Saga 정방향 단계. 순서는 HOLD × 판매자 수 → COLLECT × 1 → PAY × 판매자 수 → CONFIRM × 판매자 수.
 * 보상 단계(RELEASE, REFUND 등)는 각 정방향 단계의 역연산이며, 오케스트레이터 담당자가 확정한다.
 */
enum class SagaStepType {
    HOLD,
    COLLECT,
    PAY,
    CONFIRM,
}

enum class SagaStepStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    COMPENSATED,
    COMPENSATION_FAILED,
}

/** 체크아웃 전체 상태. 자동 보상이 1회 재시도 후에도 실패하면 [NEEDS_MANUAL_REVIEW]에서 멈춘다. */
enum class CheckoutStatus {
    IN_PROGRESS,
    COMPLETED,
    COMPENSATING,
    COMPENSATED,
    NEEDS_MANUAL_REVIEW,
}

data class SagaStepState(
    val merchantId: MerchantId,
    val type: SagaStepType,
    val status: SagaStepStatus,
    val attempt: Int,
    /** 판매자/PG가 돌려준 참조 ID(holdId, paymentKey 등). */
    val externalRef: String?,
    val updatedAt: Instant,
)

/** `checkout` + `checkout_step` 테이블에 영속되는 Saga 스냅샷. [version]은 낙관적 락에 쓴다. */
data class SagaState(
    val checkoutId: CheckoutId,
    val status: CheckoutStatus,
    val steps: List<SagaStepState>,
    val version: Long,
)
