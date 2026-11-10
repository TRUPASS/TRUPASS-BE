package com.trupass.core.domain.common

/**
 * 금액. 부동소수점 오차를 피하려고 통화의 최소 단위 정수([Long])로만 다룬다.
 * KRW는 원 단위다.
 */
data class Money(
    val amount: Long,
    val currency: CurrencyCode,
) {
    init {
        require(amount >= 0) { "amount must not be negative: $amount" }
    }

    companion object {
        fun krw(amount: Long): Money = Money(amount, CurrencyCode.KRW)
    }
}

/** ISO 4217 통화 코드(대문자 3자리). */
@JvmInline
value class CurrencyCode(
    val value: String,
) {
    init {
        require(value.length == 3 && value.all { it in 'A'..'Z' }) { "invalid ISO 4217 code: $value" }
    }

    override fun toString(): String = value

    companion object {
        val KRW = CurrencyCode("KRW")
    }
}
