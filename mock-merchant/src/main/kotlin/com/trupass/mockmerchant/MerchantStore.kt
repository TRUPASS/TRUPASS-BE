package com.trupass.mockmerchant

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 인메모리 판매자 상태. 재시작하면 사라진다.
 *
 * hold 상태 전이: HELD → (RELEASED | EXPIRED | PAID) , PAID → CONFIRMED.
 * 보상 경로 테스트를 위해 release·cancel은 같은 대상을 여러 번 불러도 성공한다.
 */
@Component
class MerchantStore(
    private val clock: Clock,
    private val properties: MockMerchantProperties,
) {
    val merchants: Map<String, Merchant> =
        listOf(
            Merchant("A", "Mock Air", Category.FLIGHT),
            Merchant("B", "Mock Stay", Category.STAY),
            Merchant("C", "Mock Experience", Category.EXPERIENCE),
        ).associateBy { it.merchantId }

    private val offers: Map<String, Offer> =
        listOf(
            Offer("A-ICN-KIX-001", "A", Category.FLIGHT, "인천 → 오사카 왕복 (이코노미)", 320_000, "KRW"),
            Offer("A-ICN-FUK-001", "A", Category.FLIGHT, "인천 → 후쿠오카 왕복 (이코노미)", 250_000, "KRW"),
            Offer("B-OSA-HOTEL-001", "B", Category.STAY, "오사카 난바 호텔 2박", 280_000, "KRW"),
            Offer("B-FUK-HOTEL-001", "B", Category.STAY, "후쿠오카 하카타 호텔 2박", 220_000, "KRW"),
            Offer("C-OSA-USJ-001", "C", Category.EXPERIENCE, "유니버설 스튜디오 재팬 1일권", 110_000, "KRW"),
            Offer("C-FUK-FOOD-001", "C", Category.EXPERIENCE, "후쿠오카 야타이 푸드 투어", 60_000, "KRW"),
        ).associateBy { it.offerId }

    private val holds = ConcurrentHashMap<String, HoldResponse>()
    private val bookings = ConcurrentHashMap<String, BookingResponse>()

    fun offersOf(merchantId: String): List<Offer> {
        requireMerchant(merchantId)
        return offers.values.filter { it.merchantId == merchantId }
    }

    fun createHold(
        merchantId: String,
        request: CreateHoldRequest,
    ): HoldResponse {
        requireMerchant(merchantId)
        val offer =
            offers[request.offerId]?.takeIf { it.merchantId == merchantId }
                ?: throw MockMerchantException(HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND", "offer ${request.offerId} not found")
        val hold =
            HoldResponse(
                holdId = "hold_${UUID.randomUUID()}",
                merchantId = merchantId,
                offerId = offer.offerId,
                quantity = request.quantity,
                amount = offer.amount * request.quantity,
                currency = offer.currency,
                status = HoldStatus.HELD,
                expiresAt = now().plus(properties.holdTtl),
            )
        holds[hold.holdId] = hold
        return hold
    }

    fun release(holdId: String): HoldResponse =
        transition(holdId) { hold ->
            when (hold.status) {
                HoldStatus.HELD, HoldStatus.RELEASED, HoldStatus.EXPIRED -> hold.copy(status = HoldStatus.RELEASED)
                else -> throw conflict("HOLD_NOT_RELEASABLE", "hold ${hold.holdId} is ${hold.status}")
            }
        }

    fun pay(
        holdId: String,
        request: PayHoldRequest,
    ): HoldResponse =
        transition(holdId) { hold ->
            when (hold.status) {
                HoldStatus.HELD -> {
                    if (request.amount != hold.amount || request.currency != hold.currency) {
                        throw MockMerchantException(
                            HttpStatus.UNPROCESSABLE_CONTENT,
                            "AMOUNT_MISMATCH",
                            "expected ${hold.amount} ${hold.currency}",
                        )
                    }
                    hold.copy(status = HoldStatus.PAID)
                }

                HoldStatus.EXPIRED -> {
                    throw MockMerchantException(HttpStatus.GONE, "HOLD_EXPIRED", "hold ${hold.holdId} expired")
                }

                else -> {
                    throw conflict("HOLD_NOT_PAYABLE", "hold ${hold.holdId} is ${hold.status}")
                }
            }
        }

    fun confirm(holdId: String): BookingResponse {
        val hold =
            transition(holdId) { hold ->
                when (hold.status) {
                    HoldStatus.PAID -> hold.copy(status = HoldStatus.CONFIRMED)
                    else -> throw conflict("HOLD_NOT_CONFIRMABLE", "hold ${hold.holdId} is ${hold.status}")
                }
            }
        val booking =
            BookingResponse(
                bookingId = "bk_${UUID.randomUUID()}",
                holdId = hold.holdId,
                merchantId = hold.merchantId,
                status = BookingStatus.CONFIRMED,
                amount = hold.amount,
                currency = hold.currency,
                updatedAt = now(),
            )
        bookings[booking.bookingId] = booking
        return booking
    }

    fun cancelBooking(bookingId: String): BookingResponse =
        bookings.compute(bookingId) { _, booking ->
            booking ?: throw MockMerchantException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "booking $bookingId not found")
            booking.copy(status = BookingStatus.CANCELLED, updatedAt = now())
        }!!

    private fun transition(
        holdId: String,
        change: (HoldResponse) -> HoldResponse,
    ): HoldResponse =
        holds.compute(holdId) { _, hold ->
            hold ?: throw MockMerchantException(HttpStatus.NOT_FOUND, "HOLD_NOT_FOUND", "hold $holdId not found")
            change(expireIfNeeded(hold))
        }!!

    private fun expireIfNeeded(hold: HoldResponse): HoldResponse =
        if (hold.status == HoldStatus.HELD && !now().isBefore(hold.expiresAt)) hold.copy(status = HoldStatus.EXPIRED) else hold

    private fun requireMerchant(merchantId: String) {
        if (merchantId !in merchants) {
            throw MockMerchantException(HttpStatus.NOT_FOUND, "MERCHANT_NOT_FOUND", "merchant $merchantId not found")
        }
    }

    private fun conflict(
        code: String,
        message: String,
    ) = MockMerchantException(HttpStatus.CONFLICT, code, message)

    private fun now(): Instant = clock.instant()
}
