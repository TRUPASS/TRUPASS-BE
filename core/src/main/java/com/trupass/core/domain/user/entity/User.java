package com.trupass.core.domain.user.entity;

import com.trupass.core.domain.user.entity.enums.Gender;
import com.trupass.core.domain.user.entity.enums.TermsType;
import com.trupass.core.domain.user.entity.enums.TravelInterest;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Trupass 사용자. 지금은 Google 계정으로만 가입한다({@code googleSub}가 Google 계정 고유 ID).
 *
 * <p>가입은 두 단계다. Google 로그인 때 계정이 생기고, 회원 정보 입력(닉네임·출생 연도·성별·관심사·약관 동의)을 마치면 {@link
 * #isProfileCompleted()}가 true가 된다.
 */
@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "google_sub", nullable = false, unique = true)
  private String googleSub;

  @Column(nullable = false, length = 320)
  private String email;

  /** Google 계정의 이름 */
  private String name;

  @Column(name = "picture_url", length = 2048)
  private String pictureUrl;

  /** 화면과 알림에 보이는 이름. 회원 정보 입력 전에는 없다. */
  @Column(length = 12)
  private String nickname;

  @Column(name = "birth_year")
  private Integer birthYear;

  /** 선택 항목. 고르지 않았으면 없다. */
  @Enumerated(EnumType.STRING)
  @Column(length = 16)
  private Gender gender;

  @ElementCollection
  @CollectionTable(name = "user_interests", joinColumns = @JoinColumn(name = "user_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "interest", nullable = false, length = 32)
  private Set<TravelInterest> interests = new HashSet<>();

  /** 동의한 약관 → 처음 동의한 시각. 동의하지 않았거나 동의를 거둔 약관은 없다. */
  @ElementCollection
  @CollectionTable(name = "user_agreements", joinColumns = @JoinColumn(name = "user_id"))
  @MapKeyEnumerated(EnumType.STRING)
  @MapKeyColumn(name = "terms_type", length = 32)
  @Column(name = "agreed_at", nullable = false)
  private Map<TermsType, Instant> agreements = new HashMap<>();

  /** 회원 정보 입력을 처음 마친 시각 */
  @Column(name = "profile_completed_at")
  private Instant profileCompletedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "last_login_at", nullable = false)
  private Instant lastLoginAt;

  protected User() {}

  private User(String googleSub, String email, String name, String pictureUrl, Instant now) {
    this.googleSub = googleSub;
    this.email = email;
    this.name = name;
    this.pictureUrl = pictureUrl;
    this.createdAt = now;
    this.updatedAt = now;
    this.lastLoginAt = now;
  }

  public static User fromGoogle(
      String googleSub, String email, String name, String pictureUrl, Instant now) {
    return new User(googleSub, email, name, pictureUrl, now);
  }

  /** 로그인할 때마다 Google 프로필(이메일·이름·사진)을 최신으로 맞춘다. */
  public void recordGoogleLogin(String email, String name, String pictureUrl, Instant now) {
    this.email = email;
    this.name = name;
    this.pictureUrl = pictureUrl;
    this.updatedAt = now;
    this.lastLoginAt = now;
  }

  /**
   * 회원 정보를 채운다. 처음 입력과 이후 수정에 같이 쓴다. 처음이면 회원 정보 입력을 마친 시각을 남긴다.
   *
   * <p>약관은 이미 동의한 것이면 처음 동의한 시각을 그대로 두고, 선택 약관에서 빠지면 동의를 거둔 것으로 보고 지운다. 필수 약관은 빠질 수 없다.
   *
   * @param agreedTerms 이번에 동의한 약관 전부
   */
  public void updateProfile(
      String nickname,
      int birthYear,
      Gender gender,
      Set<TravelInterest> interests,
      Set<TermsType> agreedTerms,
      Instant now) {
    if (!agreedTerms.containsAll(TermsType.required())) {
      throw new IllegalArgumentException("필수 약관에 모두 동의해야 합니다: " + agreedTerms);
    }
    if (interests.size() > TravelInterest.MAX_SELECTED) {
      throw new IllegalArgumentException("여행 관심사는 최대 " + TravelInterest.MAX_SELECTED + "개입니다");
    }
    this.nickname = nickname;
    this.birthYear = birthYear;
    this.gender = gender;
    // 컬렉션을 바꿔 끼우지 않고 고친다. 그대로인 값은 다시 쓰지 않는다.
    this.interests.retainAll(interests);
    this.interests.addAll(interests);
    this.agreements.keySet().retainAll(agreedTerms);
    agreedTerms.forEach(type -> this.agreements.putIfAbsent(type, now));
    if (this.profileCompletedAt == null) {
      this.profileCompletedAt = now;
    }
    this.updatedAt = now;
  }

  public Long getId() {
    return id;
  }

  public String getGoogleSub() {
    return googleSub;
  }

  public String getEmail() {
    return email;
  }

  public String getName() {
    return name;
  }

  public String getPictureUrl() {
    return pictureUrl;
  }

  public String getNickname() {
    return nickname;
  }

  public Integer getBirthYear() {
    return birthYear;
  }

  public Gender getGender() {
    return gender;
  }

  public Set<TravelInterest> getInterests() {
    return Collections.unmodifiableSet(interests);
  }

  public boolean hasAgreed(TermsType type) {
    return agreements.containsKey(type);
  }

  /** 회원 정보 입력을 마쳤는가. false면 FE가 회원 정보 입력 화면으로 보낸다. */
  public boolean isProfileCompleted() {
    return profileCompletedAt != null;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getLastLoginAt() {
    return lastLoginAt;
  }
}
