@file:Suppress("ktlint:standard:package-name") // `in`은 Kotlin 키워드라 백틱이 필요하다.

package com.trupass.core.adapter.`in`.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * PG 웹훅 수신 자리. 로컬에서는 ngrok/Cloudflare Tunnel로 공인 URL을 만들어 PG 콘솔에 등록한다(README 참고).
 * 구현 시 원문 바이트를 그대로 `PaymentPort.parseWebhook`에 넘겨 서명을 검증한다.
 */
@RestController
@RequestMapping("/webhooks")
@Tag(name = "webhooks")
class PaymentWebhookController {
    @PostMapping("/payments")
    @Operation(summary = "결제 웹훅 수신", description = "아직 구현되지 않았다(501).")
    fun receivePaymentWebhook(
        @RequestBody body: ByteArray,
    ): ResponseEntity<Void> = ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
}
