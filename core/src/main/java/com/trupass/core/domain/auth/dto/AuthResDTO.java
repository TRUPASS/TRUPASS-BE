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

  /**
   * Google 로그인 결과. {@code newUser}는 이번 로그인으로 가입했을 때만 true다. 회원 정보 입력 화면으로 보낼지는 {@code
   * user.profileCompleted}로 정한다(입력을 마치지 않고 나갔다가 다시 로그인한 경우도 잡는다).
   */
  public record Login(Tokens tokens, UserResDTO.Me user, boolean newUser) {}
}
