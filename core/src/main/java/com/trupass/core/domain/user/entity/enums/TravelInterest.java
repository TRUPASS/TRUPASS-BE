package com.trupass.core.domain.user.entity.enums;

/**
 * 여행 관심사. 추천 장소와 활동을 고르는 기준이 된다. 회원 정보 입력에서 최대 {@link #MAX_SELECTED}개 고른다.
 *
 * <p>API는 enum 이름(예: {@code FOOD})을 주고받는다. {@link #getLabel()}은 FE 회원 정보 입력 화면에 보이는 문구다.
 */
public enum TravelInterest {
  FOOD("미식 · 맛집"),
  SIGHTSEEING("관광 · 명소"),
  SHOPPING("쇼핑"),
  RELAXATION("휴양 · 온천"),
  CULTURE("문화 · 예술"),
  ARCHITECTURE("건축 · 도시 산책"),
  NATURE("자연 · 야외 활동"),
  THEME_PARK("테마파크 · 체험");

  /** 한 사람이 고를 수 있는 최대 개수 */
  public static final int MAX_SELECTED = 3;

  private final String label;

  TravelInterest(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
