@file:Suppress("ktlint:standard:package-name") // `in`은 Kotlin 키워드라 백틱이 필요하다.

package com.trupass.core.adapter.`in`.web

import com.trupass.core.domain.pkg.OfferCategory
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate

/**
 * agent 전용 내부 도구 API. agent는 외부 API(Duffel, TourAPI 등)를 직접 부르지 않고 여기만 호출한다.
 * 1단계 배포에서는 Caddy가 `/internal` 하위 경로를 외부에 노출하지 않는다.
 */
@RestController
@RequestMapping("/internal/tools")
@Tag(name = "internal-tools", description = "agent 전용 내부 도구")
class InternalToolsController {
    @PostMapping("/search-offers")
    @Operation(summary = "오퍼 검색", description = "아직 구현되지 않았다(501). MerchantPort.searchOffers에 연결 예정.")
    fun searchOffers(
        @Valid @RequestBody request: SearchOffersRequest,
    ): ResponseEntity<SearchOffersResponse> = ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
}

data class SearchOffersRequest(
    @field:NotBlank val destination: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    @field:Min(1) val travelers: Int,
    val categories: Set<OfferCategory>? = null,
)

data class SearchOffersResponse(
    val offers: List<OfferDto>,
)

/** agent의 `OfferRef` 스키마와 필드를 맞춘다(JSON은 camelCase). */
data class OfferDto(
    val offerId: String,
    val merchantId: String,
    val category: OfferCategory,
    val title: String,
    val amount: Long,
    val currency: String,
    val expiresAt: Instant?,
)
