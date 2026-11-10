package com.trupass.core.adapter.out.audit.s3

import com.trupass.core.application.port.AuditPort
import com.trupass.core.domain.mandate.AuditRef
import com.trupass.core.domain.mandate.MandateRecord
import com.trupass.core.domain.mandate.ReceiptRecord

/** S3 Object Lock 감사 저장소 자리. AWS SDK 의존성은 구현 시점에 추가한다. */
class S3ObjectLockAuditAdapter : AuditPort {
    override fun appendMandate(record: MandateRecord): AuditRef = TODO("S3 Object Lock 연동 예정")

    override fun appendReceipt(record: ReceiptRecord): AuditRef = TODO("S3 Object Lock 연동 예정")
}
