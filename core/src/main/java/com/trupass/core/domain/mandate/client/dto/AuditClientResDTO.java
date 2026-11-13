package com.trupass.core.domain.mandate.client.dto;

/** {@code AuditClient}가 돌려주는 응답 형식. */
public final class AuditClientResDTO {
  private AuditClientResDTO() {}

  /** 보관소에 기록된 위치와 무결성 해시. */
  public record Stored(String location, String sha256) {}
}
