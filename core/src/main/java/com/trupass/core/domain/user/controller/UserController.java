package com.trupass.core.domain.user.controller;

import com.trupass.core.domain.user.dto.UserResDTO;
import com.trupass.core.domain.user.service.UserService;
import com.trupass.core.global.apiPayload.ApiResponse;
import com.trupass.core.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
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
  @Operation(summary = "내 정보", description = "Authorization: Bearer {accessToken} 필요")
  public ApiResponse<UserResDTO.Me> getMe(@LoginUserId Long userId) {
    return ApiResponse.onSuccess(userService.getMe(userId));
  }
}
