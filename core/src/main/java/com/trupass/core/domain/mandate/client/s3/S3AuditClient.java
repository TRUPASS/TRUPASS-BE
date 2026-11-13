package com.trupass.core.domain.mandate.client.s3;

import com.trupass.core.domain.mandate.client.AuditClient;
import com.trupass.core.domain.mandate.client.dto.AuditClientReqDTO;
import com.trupass.core.domain.mandate.client.dto.AuditClientResDTO;

/** S3 Object Lock 보관소 자리. AWS SDK 의존성은 구현할 때 추가한다. */
public class S3AuditClient implements AuditClient {

  @Override
  public AuditClientResDTO.Stored appendMandate(AuditClientReqDTO.Mandate mandate) {
    throw new UnsupportedOperationException("S3 Object Lock 연동 예정");
  }

  @Override
  public AuditClientResDTO.Stored appendReceipt(AuditClientReqDTO.Receipt receipt) {
    throw new UnsupportedOperationException("S3 Object Lock 연동 예정");
  }
}
