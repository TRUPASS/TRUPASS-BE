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
import org.springframework.transaction.annotation.Transactional;

/**
 * Google 로그인 → Trupass 토큰 발급.
 *
 * <p>로그인은 규칙 6(트랜잭션 안에서 외부 API 금지)에 따라 트랜잭션을 열지 않는다. Google 검증(외부 호출)을 먼저 끝내고, 사용자 저장과 refresh
 * token 저장은 각 service의 트랜잭션에서 한다.
 *
 * <p>재발급은 외부 호출이 없어서 한 트랜잭션으로 묶는다(#17). 이유는 {@link #refresh} 참고.
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

  /**
   * refresh token을 소비하고 새 토큰 쌍을 발급한다. 소비 · refresh token 발급 · access token 생성을 한 트랜잭션에서 한다.
   *
   * <p>같은 토큰이 동시에 오면 뒤에 온 요청은 {@link RefreshTokenService#consume}의 행 락에서 이 트랜잭션이 커밋될 때까지 기다린 뒤
   * 재사용으로 처리된다. 새 refresh token이 따로 커밋되면 그 재사용 처리(사용자 refresh token 일괄 폐기)를 피해 살아남을 수 있어서 소비와 같은
   * 커밋에 넣는다. access token도 커밋 전에 만들어 두어야 뒤에 온 요청이 남기는 기준 시각에 걸린다.
   *
   * <p>재사용으로 판단하면 {@code consume}이 예외를 던지면서도 폐기는 커밋돼야 하므로 여기도 {@code noRollbackFor}를 둔다.
   */
  @Transactional(noRollbackFor = GeneralException.class)
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
