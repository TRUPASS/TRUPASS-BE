package com.trupass.core.domain.checkout

import com.trupass.core.domain.saga.SagaStepType

/** 체크아웃(=Saga 인스턴스) 식별자. */
@JvmInline
value class CheckoutId(
    val value: String,
) {
    override fun toString(): String = value
}

/** 판매자 식별자. Mock 판매자는 A(항공)·B(숙소)·C(체험)이다. */
@JvmInline
value class MerchantId(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        /** 사용자 → Trupass 결제(COLLECT)처럼 판매자가 아닌 Trupass 자신이 상대인 단계에 쓴다. */
        val PLATFORM = MerchantId("trupass")
    }
}

/**
 * 모든 외부 호출에 붙이는 멱등키. 형식은 `{checkout_id}:{merchant}:{step}`로 고정한다.
 * 같은 단계를 재시도할 때는 반드시 같은 키를 다시 써야 한다.
 */
@JvmInline
value class IdempotencyKey(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        fun of(
            checkoutId: CheckoutId,
            merchantId: MerchantId,
            step: SagaStepType,
        ): IdempotencyKey = IdempotencyKey("$checkoutId:$merchantId:${step.name}")
    }
}
