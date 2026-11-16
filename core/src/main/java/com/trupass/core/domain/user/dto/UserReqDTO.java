package com.trupass.core.domain.user.dto;

public final class UserReqDTO {
  private UserReqDTO() {}

  /** 검증을 마친 Google 계정 정보. auth 도메인이 만들어 넘긴다. */
  public record GoogleProfile(String sub, String email, String name, String pictureUrl) {}
}
