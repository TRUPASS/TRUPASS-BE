package com.trupass.core.domain.auth.client.dto;

public final class GoogleAuthClientResDTO {
  private GoogleAuthClientResDTO() {}

  /** 검증을 통과한 Google ID 토큰의 클레임. */
  public record IdToken(
      String sub, String email, boolean emailVerified, String name, String picture) {}
}
