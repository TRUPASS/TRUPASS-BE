package com.trupass.core.domain.merchant

import com.trupass.core.domain.checkout.CheckoutId
import com.trupass.core.domain.checkout.IdempotencyKey
import com.trupass.core.domain.checkout.MerchantId
import com.trupass.core.domain.common.Money
import com.trupass.core.domain.pkg.OfferId
import java.time.Instant

@JvmInline
value class HoldId(
    val value: String,
) {
    override fun toString(): String = value
}

@JvmInline
value class BookingId(
    val value: String,
) {
    override fun toString(): String = value
}

data class HoldRequest(
    val checkoutId: CheckoutId,
    val merchantId: MerchantId,
    val offerId: OfferId,
    val quantity: Int,
    val idempotencyKey: IdempotencyKey,
)

/** 재고만 잡은 상태. 돈은 아직 움직이지 않았다. [expiresAt]이 지나면 판매자가 자동 해제한다. */
data class Hold(
    val holdId: HoldId,
    val merchantId: MerchantId,
    val offerId: OfferId,
    val amount: Money,
    val expiresAt: Instant,
)

data class MerchantPayRequest(
    val merchantId: MerchantId,
    val holdId: HoldId,
    val amount: Money,
    val idempotencyKey: IdempotencyKey,
)

/** Trupass → 판매자 정산 결과. */
data class MerchantPayment(
    val holdId: HoldId,
    val amount: Money,
    val paidAt: Instant,
)

data class Booking(
    val bookingId: BookingId,
    val holdId: HoldId,
    val merchantId: MerchantId,
    val confirmedAt: Instant,
)

data class BookingCancellation(
    val bookingId: BookingId,
    val refundedAmount: Money?,
    val cancelledAt: Instant,
)

/** 판매자 호출 실패 사유. Saga가 재시도/보상 여부를 판단하는 근거다. */
enum class MerchantFailureReason {
    SOLD_OUT,
    HOLD_EXPIRED,
    TIMEOUT,
    UNAVAILABLE,
    REJECTED,
}

class MerchantFailureException(
    val reason: MerchantFailureReason,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
