package com.trupass.core.adapter.out.payment.mockpg

import com.trupass.core.application.port.PaymentPort
import com.trupass.core.domain.payment.CollectRequest
import com.trupass.core.domain.payment.Payment
import com.trupass.core.domain.payment.PaymentCancelRequest
import com.trupass.core.domain.payment.PaymentCancellation
import com.trupass.core.domain.payment.PaymentEvent
import com.trupass.core.domain.payment.RawWebhook

/** 인프로세스 Mock PG. 담당자가 타임아웃·실패 주입이 가능한 형태로 구현한다. */
class MockPgPaymentAdapter : PaymentPort {
    override fun collect(request: CollectRequest): Payment = TODO("담당자 구현: Mock PG 승인")

    override fun cancel(request: PaymentCancelRequest): PaymentCancellation = TODO("담당자 구현: Mock PG 부분/전체 취소")

    override fun parseWebhook(webhook: RawWebhook): PaymentEvent = TODO("담당자 구현: Mock PG 웹훅")
}
