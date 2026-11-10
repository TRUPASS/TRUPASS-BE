package com.trupass.core.adapter.out.merchant.duffel

import com.trupass.core.application.port.MerchantPort
import com.trupass.core.domain.checkout.IdempotencyKey
import com.trupass.core.domain.checkout.MerchantId
import com.trupass.core.domain.merchant.Booking
import com.trupass.core.domain.merchant.BookingCancellation
import com.trupass.core.domain.merchant.BookingId
import com.trupass.core.domain.merchant.Hold
import com.trupass.core.domain.merchant.HoldId
import com.trupass.core.domain.merchant.HoldRequest
import com.trupass.core.domain.merchant.MerchantPayRequest
import com.trupass.core.domain.merchant.MerchantPayment
import com.trupass.core.domain.pkg.Offer
import com.trupass.core.domain.pkg.OfferSearchCriteria

/** Duffel 항공·Stays 어댑터 자리. 2단계 이후 구현한다. */
class DuffelMerchantAdapter : MerchantPort {
    override fun searchOffers(criteria: OfferSearchCriteria): List<Offer> = TODO("Duffel 연동 예정")

    override fun hold(request: HoldRequest): Hold = TODO("Duffel 연동 예정")

    override fun release(
        merchantId: MerchantId,
        holdId: HoldId,
        idempotencyKey: IdempotencyKey,
    ): Unit = TODO("Duffel 연동 예정")

    override fun pay(request: MerchantPayRequest): MerchantPayment = TODO("Duffel 연동 예정")

    override fun confirm(
        merchantId: MerchantId,
        holdId: HoldId,
        idempotencyKey: IdempotencyKey,
    ): Booking = TODO("Duffel 연동 예정")

    override fun cancel(
        merchantId: MerchantId,
        bookingId: BookingId,
        idempotencyKey: IdempotencyKey,
    ): BookingCancellation = TODO("Duffel 연동 예정")
}
