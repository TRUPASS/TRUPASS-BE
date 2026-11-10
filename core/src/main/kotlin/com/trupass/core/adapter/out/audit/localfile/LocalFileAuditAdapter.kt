package com.trupass.core.adapter.out.audit.localfile

import com.trupass.core.application.port.AuditPort
import com.trupass.core.domain.mandate.AuditRef
import com.trupass.core.domain.mandate.MandateRecord
import com.trupass.core.domain.mandate.ReceiptRecord
import java.nio.file.Path

/** 로컬 append-only JSON 파일(JSON Lines) 감사 저장소. 경로는 `trupass.audit.local-dir`. */
class LocalFileAuditAdapter(
    @Suppress("unused") private val baseDir: Path,
) : AuditPort {
    override fun appendMandate(record: MandateRecord): AuditRef = TODO("담당자 구현: mandates.jsonl에 append")

    override fun appendReceipt(record: ReceiptRecord): AuditRef = TODO("담당자 구현: receipts.jsonl에 append")
}
