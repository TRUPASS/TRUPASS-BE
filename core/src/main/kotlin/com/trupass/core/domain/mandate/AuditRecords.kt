package com.trupass.core.domain.mandate

import com.trupass.core.domain.checkout.CheckoutId
import com.trupass.core.domain.checkout.MerchantId
import java.time.Instant

/** 사용자가 패스키로 서명한 AP2 Mandate 원문. 검증 로직은 WebAuthn 라이브러리 결정 후 추가한다. */
data class MandateRecord(
    val checkoutId: CheckoutId,
    val mandateId: String,
    val contentType: String,
    val payload: ByteArray,
    val signedAt: Instant,
) {
    override fun equals(other: Any?): Boolean =
        other is MandateRecord &&
            checkoutId == other.checkoutId &&
            mandateId == other.mandateId &&
            contentType == other.contentType &&
            payload.contentEquals(other.payload) &&
            signedAt == other.signedAt

    override fun hashCode(): Int = mandateId.hashCode()
}

/** 판매자·PG가 발행한 영수증 원문. */
data class ReceiptRecord(
    val checkoutId: CheckoutId,
    val merchantId: MerchantId,
    val contentType: String,
    val payload: ByteArray,
    val issuedAt: Instant,
) {
    override fun equals(other: Any?): Boolean =
        other is ReceiptRecord &&
            checkoutId == other.checkoutId &&
            merchantId == other.merchantId &&
            contentType == other.contentType &&
            payload.contentEquals(other.payload) &&
            issuedAt == other.issuedAt

    override fun hashCode(): Int = 31 * checkoutId.hashCode() + merchantId.hashCode()
}

/** 감사 저장소에 기록된 위치와 무결성 해시. */
data class AuditRef(
    val location: String,
    val sha256: String,
)
