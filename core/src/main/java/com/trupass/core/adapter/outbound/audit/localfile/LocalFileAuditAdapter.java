package com.trupass.core.adapter.outbound.audit.localfile;

import com.trupass.core.application.port.AuditPort;
import com.trupass.core.domain.mandate.AuditRef;
import com.trupass.core.domain.mandate.MandateRecord;
import com.trupass.core.domain.mandate.ReceiptRecord;
import java.nio.file.Path;

/** 로컬 append-only JSON 파일(JSON Lines) 감사 저장소. 경로는 {@code trupass.audit.local-dir}. */
public class LocalFileAuditAdapter implements AuditPort {
  private final Path baseDir;

  public LocalFileAuditAdapter(Path baseDir) {
    this.baseDir = baseDir;
  }

  @Override
  public AuditRef appendMandate(MandateRecord record) {
    throw new UnsupportedOperationException(
        "담당자 구현: " + baseDir.resolve("mandates.jsonl") + "에 append");
  }

  @Override
  public AuditRef appendReceipt(ReceiptRecord record) {
    throw new UnsupportedOperationException(
        "담당자 구현: " + baseDir.resolve("receipts.jsonl") + "에 append");
  }
}
