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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
  /** 탈퇴하면 google_sub를 이 접두어 + 회원 ID로 바꿔 Google 계정과의 연결을 끊는다(Google sub는 숫자다). */
  static final String WITHDRAWN_SUB_PREFIX = "withdrawn:";

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

  /** 탈퇴한 시각. 탈퇴하면 개인정보를 지우고 이 값을 채운다. */
  @Column(name = "withdrawn_at")
  private Instant withdrawnAt;

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
   * @return 이번에 바뀐 약관 동의(새로 동의했거나 거둔 것). 같은 값을 다시 보내면 비어 있다
   */
  public List<AgreementChange> updateProfile(
      String nickname,
      int birthYear,
      Gender gender,
      Set<TravelInterest> interests,
      Set<TermsType> agreedTerms,
      Instant now) {
    requireActive();
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
    List<AgreementChange> changes = changeAgreements(agreedTerms, now);
    if (this.profileCompletedAt == null) {
      this.profileCompletedAt = now;
    }
    this.updatedAt = now;
    return changes;
  }

  /**
   * 탈퇴. 개인정보(이메일·이름·사진·닉네임·출생 연도·성별·관심사)를 지우고 약관 동의를 모두 거둔다. 주문·서명 기록이 이 행을 가리킬 수 있어 행은 남긴다. Google
   * 계정과의 연결도 끊으므로 같은 Google 계정으로 다시 로그인하면 새 회원으로 가입된다.
   *
   * @return 거둔 약관 동의
   */
  public List<AgreementChange> withdraw(Instant now) {
    requireActive();
    List<AgreementChange> changes = changeAgreements(Set.of(), now);
    this.googleSub = WITHDRAWN_SUB_PREFIX + id;
    // email은 NOT NULL이라 실제로 닿지 않는 주소(.invalid, RFC 2606)로 바꾼다
    this.email = "withdrawn-" + id + "@deleted.invalid";
    this.name = null;
    this.pictureUrl = null;
    this.nickname = null;
    this.birthYear = null;
    this.gender = null;
    this.interests.clear();
    this.withdrawnAt = now;
    this.updatedAt = now;
    return changes;
  }

  /** 동의 상태를 {@code agreedTerms}로 맞추고 바뀐 것만 돌려준다. 이미 동의한 약관은 처음 동의한 시각을 그대로 둔다. */
  private List<AgreementChange> changeAgreements(Set<TermsType> agreedTerms, Instant now) {
    List<AgreementChange> changes = new ArrayList<>();
    for (TermsType type : TermsType.values()) {
      boolean before = agreements.containsKey(type);
      boolean after = agreedTerms.contains(type);
      if (before && !after) {
        agreements.remove(type);
        changes.add(new AgreementChange(type, false));
      } else if (!before && after) {
        agreements.put(type, now);
        changes.add(new AgreementChange(type, true));
      }
    }
    return changes;
  }

  private void requireActive() {
    if (isWithdrawn()) {
      throw new IllegalStateException("탈퇴한 회원입니다: " + id);
    }
  }

  /** 약관 동의가 바뀐 것 하나. {@code agreed}가 false면 동의를 거뒀다. */
  public record AgreementChange(TermsType type, boolean agreed) {}

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

  /** 지금 유지 중인 동의를 한 시각. 동의하지 않았으면 null */
  public Instant agreedAt(TermsType type) {
    return agreements.get(type);
  }

  /** 회원 정보 입력을 마쳤는가. false면 FE가 회원 정보 입력 화면으로 보낸다. */
  public boolean isProfileCompleted() {
    return profileCompletedAt != null;
  }

  public boolean isWithdrawn() {
    return withdrawnAt != null;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getLastLoginAt() {
    return lastLoginAt;
  }
}
