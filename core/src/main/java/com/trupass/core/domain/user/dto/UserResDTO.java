package com.trupass.core.domain.user.dto;

import com.trupass.core.domain.user.entity.User;
import java.time.Instant;

public final class UserResDTO {
  private UserResDTO() {}

  public record Me(Long userId, String email, String name, String pictureUrl, Instant createdAt) {

    public static Me from(User user) {
      return new Me(
          user.getId(), user.getEmail(), user.getName(), user.getPictureUrl(), user.getCreatedAt());
    }
  }

  /** Google 로그인 결과. {@code newUser}가 true면 이번에 가입했다. */
  public record GoogleLogin(Me user, boolean newUser) {}
}
