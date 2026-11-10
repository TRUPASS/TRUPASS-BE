package com.trupass.core.domain.payment

import com.trupass.core.domain.checkout.CheckoutId
import com.trupass.core.domain.checkout.IdempotencyKey
import com.trupass.core.domain.common.Money
import java.time.Instant

/** PG가 발급한 결제 식별자(토스의 paymentKey 등). */
@JvmInline
value class PaymentId(
    val value: String,
) {
    override fun toString(): String = value
}

/** 사용자 → Trupass 결제 승인 요청. 체크아웃당 1건이다. */
data class CollectRequest(
    val checkoutId: CheckoutId,
    val amount: Money,
    /** FE 결제창이 돌려준 PG 측 인증 토큰. */
    val paymentToken: String,
    val idempotencyKey: IdempotencyKey,
)

data class Payment(
    val paymentId: PaymentId,
    val checkoutId: CheckoutId,
    val amount: Money,
    val approvedAt: Instant,
)

data class PaymentCancelRequest(
    val paymentId: PaymentId,
    /** null이면 전체 취소, 값이 있으면 그 금액만큼 부분 취소. */
    val cancelAmount: Money?,
    val reason: String,
    val idempotencyKey: IdempotencyKey,
)

data class PaymentCancellation(
    val paymentId: PaymentId,
    val cancelledAmount: Money,
    val remainingAmount: Money,
    val cancelledAt: Instant,
)

/** PG 웹훅 원문. 서명 검증은 어댑터가 한다. */
data class RawWebhook(
    val headers: Map<String, List<String>>,
    val body: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is RawWebhook && headers == other.headers && body.contentEquals(other.body)

    override fun hashCode(): Int = 31 * headers.hashCode() + body.contentHashCode()
}

enum class PaymentEventType {
    APPROVED,
    CANCELLED,
    PARTIALLY_CANCELLED,
    FAILED,
    UNKNOWN,
}

data class PaymentEvent(
    val paymentId: PaymentId,
    val type: PaymentEventType,
    val occurredAt: Instant,
)
