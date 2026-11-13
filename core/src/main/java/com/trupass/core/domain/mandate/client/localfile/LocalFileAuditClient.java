package com.trupass.core.domain.mandate.client.localfile;

import com.trupass.core.domain.mandate.client.AuditClient;
import com.trupass.core.domain.mandate.client.dto.AuditClientReqDTO;
import com.trupass.core.domain.mandate.client.dto.AuditClientResDTO;
import java.nio.file.Path;

/** 로컬 append-only 파일(JSON Lines) 보관소. 경로는 {@code trupass.audit.local-dir}. */
public class LocalFileAuditClient implements AuditClient {
  private final Path baseDir;

  public LocalFileAuditClient(Path baseDir) {
    this.baseDir = baseDir;
  }

  @Override
  public AuditClientResDTO.Stored appendMandate(AuditClientReqDTO.Mandate mandate) {
    throw new UnsupportedOperationException(
        "담당자 구현: " + baseDir.resolve("mandates.jsonl") + "에 append");
  }

  @Override
  public AuditClientResDTO.Stored appendReceipt(AuditClientReqDTO.Receipt receipt) {
    throw new UnsupportedOperationException(
        "담당자 구현: " + baseDir.resolve("receipts.jsonl") + "에 append");
  }
}
