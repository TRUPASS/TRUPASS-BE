package com.trupass.core.domain.auth.service;

import com.trupass.core.domain.auth.client.GoogleAuthClient;
import com.trupass.core.domain.auth.client.dto.GoogleAuthClientResDTO;
import com.trupass.core.domain.auth.dto.AuthReqDTO;
import com.trupass.core.domain.auth.dto.AuthResDTO;
import com.trupass.core.domain.auth.exception.AuthErrorCode;
import com.trupass.core.domain.user.dto.UserReqDTO;
import com.trupass.core.domain.user.dto.UserResDTO;
import com.trupass.core.domain.user.service.UserService;
import com.trupass.core.global.apiPayload.exception.GeneralException;
import com.trupass.core.global.config.TrupassProperties;
import com.trupass.core.global.security.JwtTokenProvider;
import org.springframework.stereotype.Service;

/**
 * Google 로그인 → Trupass 토큰 발급.
 *
 * <p>규칙 6(트랜잭션 안에서 외부 API 금지)에 따라 이 클래스는 트랜잭션을 열지 않는다. Google 검증(외부 호출)을 먼저 끝내고, 사용자 저장과 refresh
 * token 저장은 각 service의 트랜잭션에서 한다.
 */
@Service
public class AuthService {
  private static final String TOKEN_TYPE = "Bearer";

  private final GoogleAuthClient googleAuthClient;
  private final UserService userService;
  private final RefreshTokenService refreshTokenService;
  private final JwtTokenProvider jwtTokenProvider;
  private final TrupassProperties properties;

  public AuthService(
      GoogleAuthClient googleAuthClient,
      UserService userService,
      RefreshTokenService refreshTokenService,
      JwtTokenProvider jwtTokenProvider,
      TrupassProperties properties) {
    this.googleAuthClient = googleAuthClient;
    this.userService = userService;
    this.refreshTokenService = refreshTokenService;
    this.jwtTokenProvider = jwtTokenProvider;
    this.properties = properties;
  }

  public AuthResDTO.Login loginWithGoogle(AuthReqDTO.GoogleLogin request) {
    GoogleAuthClientResDTO.IdToken idToken = googleAuthClient.verifyIdToken(request.idToken());
    if (!idToken.emailVerified()) {
      throw new GeneralException(AuthErrorCode.GOOGLE_EMAIL_NOT_VERIFIED);
    }
    UserResDTO.GoogleLogin login =
        userService.loginWithGoogle(
            new UserReqDTO.GoogleProfile(
                idToken.sub(), idToken.email(), idToken.name(), idToken.picture()));
    return new AuthResDTO.Login(issueTokens(login.user().userId()), login.user(), login.newUser());
  }

  public AuthResDTO.Tokens refresh(AuthReqDTO.Refresh request) {
    Long userId = refreshTokenService.consume(request.refreshToken());
    return issueTokens(userId);
  }

  public void logout(AuthReqDTO.Logout request) {
    refreshTokenService.revoke(request.refreshToken());
  }

  private AuthResDTO.Tokens issueTokens(Long userId) {
    var jwt = properties.auth().jwt();
    return new AuthResDTO.Tokens(
        TOKEN_TYPE,
        jwtTokenProvider.createAccessToken(userId),
        jwt.accessTokenTtl().toSeconds(),
        refreshTokenService.issue(userId),
        jwt.refreshTokenTtl().toSeconds());
  }
}
