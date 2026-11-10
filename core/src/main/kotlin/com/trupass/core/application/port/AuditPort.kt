package com.trupass.core.application.port

import com.trupass.core.domain.mandate.AuditRef
import com.trupass.core.domain.mandate.MandateRecord
import com.trupass.core.domain.mandate.ReceiptRecord

/**
 * Mandate 원문과 영수증을 수정 불가능하게 보관하는 포트. 추가(append)만 있고 수정·삭제는 없다.
 *
 * 어댑터: `trupass.adapters.audit=localfile`(append-only JSON 파일) | `s3`(S3 Object Lock).
 */
interface AuditPort {
    /** 서명된 Mandate 원문을 기록하고 위치와 SHA-256 해시를 돌려준다. */
    fun appendMandate(record: MandateRecord): AuditRef

    /** 판매자·PG 영수증 원문을 기록하고 위치와 SHA-256 해시를 돌려준다. */
    fun appendReceipt(record: ReceiptRecord): AuditRef
}
