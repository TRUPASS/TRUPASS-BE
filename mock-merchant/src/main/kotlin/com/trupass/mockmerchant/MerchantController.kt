package com.trupass.mockmerchant

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Mock 판매자 API. 모든 엔드포인트는 [FailureInjectionFilter]의 실패 주입과
 * [IdempotencyFilter]의 멱등 처리를 거친다.
 */
@RestController
class MerchantController(
    private val store: MerchantStore,
) {
    @GetMapping("/merchants")
    fun merchants(): Collection<Merchant> = store.merchants.values

    @GetMapping("/merchants/{merchantId}/offers")
    fun offers(
        @PathVariable merchantId: String,
    ): List<Offer> = store.offersOf(merchantId)

    @PostMapping("/merchants/{merchantId}/holds")
    @ResponseStatus(HttpStatus.CREATED)
    fun createHold(
        @PathVariable merchantId: String,
        @Valid @RequestBody request: CreateHoldRequest,
    ): HoldResponse = store.createHold(merchantId, request)

    @PostMapping("/holds/{holdId}/release")
    fun release(
        @PathVariable holdId: String,
    ): HoldResponse = store.release(holdId)

    @PostMapping("/holds/{holdId}/pay")
    fun pay(
        @PathVariable holdId: String,
        @Valid @RequestBody request: PayHoldRequest,
    ): HoldResponse = store.pay(holdId, request)

    @PostMapping("/holds/{holdId}/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    fun confirm(
        @PathVariable holdId: String,
    ): BookingResponse = store.confirm(holdId)

    @PostMapping("/bookings/{bookingId}/cancel")
    fun cancel(
        @PathVariable bookingId: String,
    ): BookingResponse = store.cancelBooking(bookingId)
}

@RestControllerAdvice
class MockMerchantExceptionHandler {
    @ExceptionHandler(MockMerchantException::class)
    fun handle(e: MockMerchantException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(e.status).body(ErrorResponse(e.code, e.message ?: e.code))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .badRequest()
            .body(ErrorResponse("INVALID_REQUEST", e.bindingResult.fieldErrors.joinToString { "${it.field}: ${it.defaultMessage}" }))
}
