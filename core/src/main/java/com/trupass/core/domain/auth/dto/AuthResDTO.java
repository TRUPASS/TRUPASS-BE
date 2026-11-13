package com.trupass.core.domain.auth.dto;

import com.trupass.core.domain.user.dto.UserResDTO;

public final class AuthResDTO {
  private AuthResDTO() {}

  /**
   * Trupass 토큰 쌍. API 호출은 {@code Authorization: Bearer {accessToken}}, 만료되면 {@code POST
   * /auth/refresh}로 새 쌍을 받는다(refresh token도 바뀐다).
   */
  public record Tokens(
      String tokenType,
      String accessToken,
      long accessTokenExpiresIn,
      String refreshToken,
      long refreshTokenExpiresIn) {}

  public record Login(Tokens tokens, UserResDTO.Me user, boolean newUser) {}
}
