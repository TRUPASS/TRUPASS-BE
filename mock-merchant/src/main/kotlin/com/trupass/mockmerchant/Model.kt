package com.trupass.mockmerchant

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import java.time.Instant

enum class Category { FLIGHT, STAY, EXPERIENCE }

/** 판매자 3곳: A=항공, B=숙소, C=체험. */
data class Merchant(
    val merchantId: String,
    val name: String,
    val category: Category,
)

data class Offer(
    val offerId: String,
    val merchantId: String,
    val category: Category,
    val title: String,
    val amount: Long,
    val currency: String,
)

enum class HoldStatus { HELD, RELEASED, PAID, CONFIRMED, EXPIRED }

data class HoldResponse(
    val holdId: String,
    val merchantId: String,
    val offerId: String,
    val quantity: Int,
    val amount: Long,
    val currency: String,
    val status: HoldStatus,
    val expiresAt: Instant,
)

enum class BookingStatus { CONFIRMED, CANCELLED }

data class BookingResponse(
    val bookingId: String,
    val holdId: String,
    val merchantId: String,
    val status: BookingStatus,
    val amount: Long,
    val currency: String,
    val updatedAt: Instant,
)

data class CreateHoldRequest(
    @field:NotBlank val offerId: String,
    @field:Min(1) val quantity: Int = 1,
)

data class PayHoldRequest(
    @field:Min(0) val amount: Long,
    @field:NotBlank val currency: String,
)

data class ErrorResponse(
    val code: String,
    val message: String,
)

class MockMerchantException(
    val status: HttpStatus,
    val code: String,
    message: String,
) : RuntimeException(message)
