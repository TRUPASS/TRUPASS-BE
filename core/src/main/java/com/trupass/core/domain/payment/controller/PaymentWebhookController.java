package com.trupass.core.domain.payment.controller;

import com.trupass.core.domain.payment.service.PaymentService;
import com.trupass.core.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PG 웹훅 수신. 로컬에서는 ngrok/Cloudflare Tunnel로 공인 URL을 만들어 PG 콘솔에 등록한다(README 참고). 서명 검증을 위해 원문 바이트를
 * 그대로 service에 넘긴다.
 */
@RestController
@RequestMapping("/webhooks")
@Tag(name = "webhooks")
public class PaymentWebhookController {
  private final PaymentService paymentService;

  public PaymentWebhookController(PaymentService paymentService) {
    this.paymentService = paymentService;
  }

  @PostMapping("/payments")
  @Operation(summary = "결제 웹훅 수신", description = "PaymentClient 구현 전까지는 501(COMMON501)을 돌려준다.")
  public ApiResponse<Void> receivePaymentWebhook(
      @RequestHeader HttpHeaders headers, @RequestBody byte[] body) {
    Map<String, List<String>> rawHeaders = new LinkedHashMap<>();
    headers.forEach(rawHeaders::put);
    paymentService.handleWebhook(rawHeaders, body);
    return ApiResponse.onSuccess(null);
  }
}
