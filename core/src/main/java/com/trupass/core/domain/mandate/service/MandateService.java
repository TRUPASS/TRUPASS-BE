package com.trupass.core.domain.mandate.service;

import com.trupass.core.domain.mandate.client.AuditClient;
import com.trupass.core.domain.mandate.client.dto.AuditClientReqDTO;
import com.trupass.core.domain.mandate.client.dto.AuditClientResDTO;
import org.springframework.stereotype.Service;

/** 사용자 서명(AP2 Mandate) 검증과 서명 원본·영수증 보관. 서명 검증은 WebAuthn 라이브러리를 정한 뒤 추가한다(ADR-0003). */
@Service
public class MandateService {
  private final AuditClient auditClient;

  public MandateService(AuditClient auditClient) {
    this.auditClient = auditClient;
  }

  public AuditClientResDTO.Stored storeMandate(AuditClientReqDTO.Mandate mandate) {
    return auditClient.appendMandate(mandate);
  }

  public AuditClientResDTO.Stored storeReceipt(AuditClientReqDTO.Receipt receipt) {
    return auditClient.appendReceipt(receipt);
  }
}
