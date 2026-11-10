package com.trupass.core.adapter.out.merchant.mock

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
import org.springframework.web.client.RestClient

/**
 * `mock-merchant` 서비스(HTTP)를 부르는 어댑터. 공통 RestClient(타임아웃·checkout_id 로깅)를 주입받는다.
 * 멱등키는 [com.trupass.core.config.http.idempotencyKey] 확장 함수로 붙인다.
 */
class MockMerchantAdapter(
    @Suppress("unused") private val restClient: RestClient,
) : MerchantPort {
    override fun searchOffers(criteria: OfferSearchCriteria): List<Offer> = TODO("담당자 구현: GET /merchants/{id}/offers")

    override fun hold(request: HoldRequest): Hold = TODO("담당자 구현: POST /merchants/{id}/holds")

    override fun release(
        merchantId: MerchantId,
        holdId: HoldId,
        idempotencyKey: IdempotencyKey,
    ): Unit = TODO("담당자 구현: POST /holds/{holdId}/release")

    override fun pay(request: MerchantPayRequest): MerchantPayment = TODO("담당자 구현: POST /holds/{holdId}/pay")

    override fun confirm(
        merchantId: MerchantId,
        holdId: HoldId,
        idempotencyKey: IdempotencyKey,
    ): Booking = TODO("담당자 구현: POST /holds/{holdId}/confirm")

    override fun cancel(
        merchantId: MerchantId,
        bookingId: BookingId,
        idempotencyKey: IdempotencyKey,
    ): BookingCancellation = TODO("담당자 구현: POST /bookings/{id}/cancel")
}
