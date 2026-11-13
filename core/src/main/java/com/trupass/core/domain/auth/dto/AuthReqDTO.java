package com.trupass.core.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public final class AuthReqDTO {
  private AuthReqDTO() {}

  /** FE가 Google Identity Services(One Tap / 로그인 버튼)로 받은 {@code credential}(ID 토큰). */
  public record GoogleLogin(@NotBlank String idToken) {}

  public record Refresh(@NotBlank String refreshToken) {}

  public record Logout(@NotBlank String refreshToken) {}
}
