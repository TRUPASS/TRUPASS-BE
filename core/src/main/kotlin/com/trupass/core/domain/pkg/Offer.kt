package com.trupass.core.domain.pkg

import com.trupass.core.domain.checkout.MerchantId
import com.trupass.core.domain.common.Money
import java.time.Instant
import java.time.LocalDate

@JvmInline
value class OfferId(
    val value: String,
) {
    override fun toString(): String = value
}

enum class OfferCategory {
    FLIGHT,
    STAY,
    EXPERIENCE,
}

/** 판매자가 제시한 상품 1건. 금액은 반드시 오퍼 ID와 함께 다닌다. */
data class Offer(
    val offerId: OfferId,
    val merchantId: MerchantId,
    val category: OfferCategory,
    val title: String,
    val price: Money,
    val expiresAt: Instant?,
)

data class OfferSearchCriteria(
    val destination: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val travelers: Int,
    val categories: Set<OfferCategory> = OfferCategory.entries.toSet(),
)
