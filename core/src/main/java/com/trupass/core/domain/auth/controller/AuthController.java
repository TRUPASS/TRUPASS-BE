package com.trupass.core.domain.auth.controller;

import com.trupass.core.domain.auth.dto.AuthReqDTO;
import com.trupass.core.domain.auth.dto.AuthResDTO;
import com.trupass.core.domain.auth.service.AuthService;
import com.trupass.core.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인·토큰. 이 경로들은 access token 없이 부른다. */
@RestController
@RequestMapping("/auth")
@Tag(name = "auth")
@SecurityRequirements
public class AuthController {
  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/google")
  @Operation(
      summary = "Google 로그인",
      description =
          "FE가 Google Identity Services로 받은 ID 토큰(credential)을 보내면 검증 후 Trupass 토큰을 발급한다. 처음이면 가입된다.")
  public ApiResponse<AuthResDTO.Login> loginWithGoogle(
      @Valid @RequestBody AuthReqDTO.GoogleLogin request) {
    return ApiResponse.onSuccess(authService.loginWithGoogle(request));
  }

  @PostMapping("/refresh")
  @Operation(summary = "토큰 재발급", description = "refresh token은 한 번만 쓸 수 있고, 새 토큰 쌍을 돌려준다.")
  public ApiResponse<AuthResDTO.Tokens> refresh(@Valid @RequestBody AuthReqDTO.Refresh request) {
    return ApiResponse.onSuccess(authService.refresh(request));
  }

  @PostMapping("/logout")
  @Operation(summary = "로그아웃", description = "refresh token을 폐기한다. access token은 만료될 때까지 유효하다.")
  public ApiResponse<Void> logout(@Valid @RequestBody AuthReqDTO.Logout request) {
    authService.logout(request);
    return ApiResponse.onSuccess(null);
  }
}
