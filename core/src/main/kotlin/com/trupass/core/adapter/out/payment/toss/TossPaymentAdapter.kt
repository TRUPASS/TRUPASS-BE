package com.trupass.core.adapter.out.payment.toss

import com.trupass.core.application.port.PaymentPort
import com.trupass.core.domain.payment.CollectRequest
import com.trupass.core.domain.payment.Payment
import com.trupass.core.domain.payment.PaymentCancelRequest
import com.trupass.core.domain.payment.PaymentCancellation
import com.trupass.core.domain.payment.PaymentEvent
import com.trupass.core.domain.payment.RawWebhook

/** 토스페이먼츠 어댑터 자리. 키는 환경 변수 `TOSS_SECRET_KEY`로 받는다. */
class TossPaymentAdapter : PaymentPort {
    override fun collect(request: CollectRequest): Payment = TODO("토스페이먼츠 연동 예정")

    override fun cancel(request: PaymentCancelRequest): PaymentCancellation = TODO("토스페이먼츠 연동 예정")

    override fun parseWebhook(webhook: RawWebhook): PaymentEvent = TODO("토스페이먼츠 연동 예정")
}
