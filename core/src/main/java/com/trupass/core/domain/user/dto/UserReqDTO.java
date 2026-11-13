package com.trupass.core.domain.user.dto;

import com.trupass.core.domain.user.entity.enums.Gender;
import com.trupass.core.domain.user.entity.enums.TermsType;
import com.trupass.core.domain.user.entity.enums.TravelInterest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.EnumSet;
import java.util.Set;

public final class UserReqDTO {
  private UserReqDTO() {}

  private static final String REQUIRED_TERMS = "필수 약관에 동의해야 합니다.";

  /** 검증을 마친 Google 계정 정보. auth 도메인이 만들어 넘긴다. */
  public record GoogleProfile(String sub, String email, String name, String pictureUrl) {}

  /**
   * 회원 정보 입력·수정. FE 회원 정보 입력 화면(/signup)의 칸과 같다. 이메일은 Google 계정의 것이라 받지 않는다.
   *
   * <p>검증에 실패하면 {@code COMMON400_1}이고 {@code result}에 {@code {칸: 메시지}}가 담긴다(예: {@code {"nickname":
   * "닉네임은 2~12자로 입력해 주세요."}}).
   */
  @Schema(description = "회원 정보 입력·수정")
  public record Profile(
      @Schema(description = "닉네임. 앞뒤 공백은 지운다. 2~12자", example = "도쿄여행자")
          @NotBlank(message = "닉네임을 입력해 주세요.")
          @Size(min = 2, max = 12, message = "닉네임은 2~12자로 입력해 주세요.")
          @Pattern(regexp = "[^\\x00-\\x1F\\x7F]*", message = "닉네임에 줄바꿈 같은 제어 문자는 쓸 수 없습니다.")
          String nickname,
      @Schema(description = "출생 연도. 1900년부터 올해까지", example = "1994")
          @NotNull(message = "출생 연도를 골라 주세요.")
          @Min(value = 1900, message = "출생 연도가 올바르지 않습니다.")
          Integer birthYear,
      @Schema(description = "성별. 선택 항목이라 \"선택 안 함\"이면 보내지 않거나 null", nullable = true) Gender gender,
      @Schema(
              description = "여행 관심사. 0~3개. 빠뜨리면 빈 목록으로 본다",
              example = "[\"CULTURE\", \"ARCHITECTURE\"]")
          @Size(max = TravelInterest.MAX_SELECTED, message = "여행 관심사는 최대 {max}개까지 고를 수 있습니다.")
          Set<@NotNull(message = "여행 관심사에 빈 값이 있습니다.") TravelInterest> interests,
      @Schema(description = "약관 동의") @NotNull(message = "약관 동의 여부를 보내 주세요.") @Valid
          Agreements agreements) {

    public Profile {
      nickname = nickname == null ? null : nickname.strip();
      interests = interests == null ? Set.of() : interests;
    }
  }

  /** 약관 동의. 필수 약관(이용약관, 개인정보 수집·이용)은 true여야 한다. */
  @Schema(description = "약관 동의. termsOfService · privacyCollection 은 true 여야 한다")
  public record Agreements(
      @Schema(description = "(필수) Trupass 이용약관", example = "true")
          @NotNull(message = REQUIRED_TERMS)
          @AssertTrue(message = REQUIRED_TERMS)
          Boolean termsOfService,
      @Schema(description = "(필수) 개인정보 수집 · 이용", example = "true")
          @NotNull(message = REQUIRED_TERMS)
          @AssertTrue(message = REQUIRED_TERMS)
          Boolean privacyCollection,
      @Schema(description = "(선택) 여행 혜택 · 알림 수신. 보내지 않으면 false", example = "false")
          Boolean marketing) {

    /** 동의한 약관 */
    public Set<TermsType> agreedTerms() {
      Set<TermsType> agreed = EnumSet.noneOf(TermsType.class);
      if (Boolean.TRUE.equals(termsOfService)) {
        agreed.add(TermsType.TERMS_OF_SERVICE);
      }
      if (Boolean.TRUE.equals(privacyCollection)) {
        agreed.add(TermsType.PRIVACY_COLLECTION);
      }
      if (Boolean.TRUE.equals(marketing)) {
        agreed.add(TermsType.MARKETING);
      }
      return agreed;
    }
  }
}
