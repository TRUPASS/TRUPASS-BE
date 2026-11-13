package com.trupass.core.domain.user.entity.enums;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** 회원 정보 입력에서 받는 약관 동의. 필수 약관에 모두 동의해야 회원 정보 입력을 마칠 수 있다. */
public enum TermsType {
  /** (필수) Trupass 이용약관 */
  TERMS_OF_SERVICE(true),
  /** (필수) 개인정보 수집 · 이용 */
  PRIVACY_COLLECTION(true),
  /** (선택) 여행 혜택 · 알림 수신(마케팅 정보 수신) */
  MARKETING(false);

  private final boolean required;

  TermsType(boolean required) {
    this.required = required;
  }

  public boolean isRequired() {
    return required;
  }

  public static Set<TermsType> required() {
    return Arrays.stream(values()).filter(TermsType::isRequired).collect(Collectors.toSet());
  }
}
