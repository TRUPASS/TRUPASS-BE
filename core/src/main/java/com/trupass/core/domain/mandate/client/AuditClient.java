package com.trupass.core.domain.mandate.client;

import com.trupass.core.domain.mandate.client.dto.AuditClientReqDTO;
import com.trupass.core.domain.mandate.client.dto.AuditClientResDTO;

/**
 * 서명 원본·영수증을 수정할 수 없게 보관한다. 추가(append)만 있고 수정·삭제는 없다.
 *
 * <p>구현체: {@code trupass.clients.audit=localfile}(append-only JSON Lines 파일) | {@code s3}(S3 Object
 * Lock). 연결은 {@code global/config/ClientConfig}에서만 한다.
 */
public interface AuditClient {

  /** 서명된 Mandate 원문을 기록하고 위치와 SHA-256 해시를 돌려준다. */
  AuditClientResDTO.Stored appendMandate(AuditClientReqDTO.Mandate mandate);

  /** 판매자·PG 영수증 원문을 기록하고 위치와 SHA-256 해시를 돌려준다. */
  AuditClientResDTO.Stored appendReceipt(AuditClientReqDTO.Receipt receipt);
}
