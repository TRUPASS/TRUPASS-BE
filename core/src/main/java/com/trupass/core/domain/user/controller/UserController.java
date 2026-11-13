package com.trupass.core.domain.user.controller;

import com.trupass.core.domain.user.dto.UserReqDTO;
import com.trupass.core.domain.user.dto.UserResDTO;
import com.trupass.core.domain.user.service.UserService;
import com.trupass.core.global.apiPayload.ApiResponse;
import com.trupass.core.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@Tag(name = "users")
public class UserController {
  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping("/me")
  @Operation(
      summary = "내 정보",
      description =
          "Authorization: Bearer {accessToken} 필요. profileCompleted 가 false 면 회원 정보 입력을 아직 마치지 않았다.")
  public ApiResponse<UserResDTO.Me> getMe(@LoginUserId Long userId) {
    return ApiResponse.onSuccess(userService.getMe(userId));
  }

  @PutMapping("/me/profile")
  @Operation(
      summary = "회원 정보 입력·수정",
      description =
          "회원 정보 입력 화면(/signup)의 닉네임 · 출생 연도 · 성별 · 여행 관심사 · 약관 동의를 저장한다. 처음 저장하면 "
              + "profileCompleted 가 true 가 된다. 설정에서 고칠 때도 같은 형식으로 전체를 보낸다(같은 값을 다시 보내도 결과가 같다). "
              + "필수 약관에 동의하지 않았거나 칸이 틀리면 COMMON400_1, 출생 연도가 올해보다 뒤면 USER400_1.")
  public ApiResponse<UserResDTO.Me> updateProfile(
      @LoginUserId Long userId, @Valid @RequestBody UserReqDTO.Profile request) {
    return ApiResponse.onSuccess(userService.updateProfile(userId, request));
  }

  @DeleteMapping("/me")
  @Operation(
      summary = "회원 탈퇴",
      description =
          "개인정보(이메일 · 이름 · 사진 · 닉네임 · 출생 연도 · 성별 · 관심사)를 지우고 약관 동의를 모두 거두며, 이 회원의 refresh token 을 모두 "
              + "폐기한다. FE 는 성공하면 이 기기의 로그인 상태도 지운다. 이미 발급한 access token 은 만료(30분)까지 남지만 그 토큰으로 오는 "
              + "요청은 USER401_1 로 거절한다. 같은 Google 계정으로 다시 로그인하면 새 회원으로 가입된다.")
  public ApiResponse<Void> withdraw(@LoginUserId Long userId) {
    userService.withdraw(userId);
    return ApiResponse.onSuccess(null);
  }
}
