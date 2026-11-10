package com.trupass.core.adapter.outbound.audit.s3;

import com.trupass.core.application.port.AuditPort;
import com.trupass.core.domain.mandate.AuditRef;
import com.trupass.core.domain.mandate.MandateRecord;
import com.trupass.core.domain.mandate.ReceiptRecord;

/** S3 Object Lock 감사 저장소 자리. AWS SDK 의존성은 구현 시점에 추가한다. */
public class S3ObjectLockAuditAdapter implements AuditPort {

  @Override
  public AuditRef appendMandate(MandateRecord record) {
    throw new UnsupportedOperationException("S3 Object Lock 연동 예정");
  }

  @Override
  public AuditRef appendReceipt(ReceiptRecord record) {
    throw new UnsupportedOperationException("S3 Object Lock 연동 예정");
  }
}
