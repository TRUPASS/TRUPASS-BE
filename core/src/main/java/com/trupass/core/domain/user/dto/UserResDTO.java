package com.trupass.core.domain.user.dto;

import com.trupass.core.domain.user.entity.User;
import com.trupass.core.domain.user.entity.enums.Gender;
import com.trupass.core.domain.user.entity.enums.TermsType;
import com.trupass.core.domain.user.entity.enums.TravelInterest;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public final class UserResDTO {
  private UserResDTO() {}

  /** 내 정보. {@code GET /users/me}와 로그인 응답의 {@code user}가 이 형식이다. */
  public record Me(
      Long userId,
      String email,
      @Schema(description = "Google 계정의 이름. 없을 수 있다") String name,
      @Schema(description = "Google 계정의 프로필 사진 주소. 없을 수 있다") String pictureUrl,
      @Schema(description = "닉네임. 회원 정보 입력 전에는 null") String nickname,
      @Schema(description = "출생 연도. 회원 정보 입력 전에는 null") Integer birthYear,
      @Schema(description = "성별. 고르지 않았으면 null") Gender gender,
      @Schema(description = "여행 관심사(0~3개)") List<TravelInterest> interests,
      @Schema(description = "약관 동의") AgreementStatus agreements,
      @Schema(description = "회원 정보 입력을 마쳤는가. false 면 회원 정보 입력(/signup)으로 보낸다")
          boolean profileCompleted,
      Instant createdAt) {

    public static Me from(User user) {
      return new Me(
          user.getId(),
          user.getEmail(),
          user.getName(),
          user.getPictureUrl(),
          user.getNickname(),
          user.getBirthYear(),
          user.getGender(),
          user.getInterests().stream().sorted().toList(),
          AgreementStatus.from(user),
          user.isProfileCompleted(),
          user.getCreatedAt());
    }
  }

  /** 약관 동의 여부. 요청({@code UserReqDTO.Agreements})과 같은 모양이다. */
  public record AgreementStatus(
      boolean termsOfService, boolean privacyCollection, boolean marketing) {

    static AgreementStatus from(User user) {
      return new AgreementStatus(
          user.hasAgreed(TermsType.TERMS_OF_SERVICE),
          user.hasAgreed(TermsType.PRIVACY_COLLECTION),
          user.hasAgreed(TermsType.MARKETING));
    }
  }

  /** Google 로그인 결과. {@code newUser}가 true면 이번에 가입했다. */
  public record GoogleLogin(Me user, boolean newUser) {}
}
