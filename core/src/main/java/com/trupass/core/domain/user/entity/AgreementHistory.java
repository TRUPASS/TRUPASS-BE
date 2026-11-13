package com.trupass.core.domain.user.entity;

import com.trupass.core.domain.user.entity.enums.TermsType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 약관 동의·철회 기록 한 줄. 지우거나 고치지 않고 쌓기만 한다. 마케팅(여행 혜택·알림) 수신을 언제 동의하고 언제 거뒀는지 이것으로 증명한다.
 *
 * <p>지금 상태는 {@link User}의 약관 동의(user_agreements)가 갖고, 이 테이블은 바뀐 기록만 갖는다.
 */
@Entity
@Table(name = "user_agreement_histories")
public class AgreementHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "terms_type", nullable = false, length = 32)
  private TermsType termsType;

  /** true면 동의, false면 동의를 거뒀다(선택 약관 해제 또는 탈퇴) */
  @Column(nullable = false)
  private boolean agreed;

  @Column(name = "changed_at", nullable = false)
  private Instant changedAt;

  protected AgreementHistory() {}

  public AgreementHistory(Long userId, TermsType termsType, boolean agreed, Instant changedAt) {
    this.userId = userId;
    this.termsType = termsType;
    this.agreed = agreed;
    this.changedAt = changedAt;
  }

  public Long getUserId() {
    return userId;
  }

  public TermsType getTermsType() {
    return termsType;
  }

  public boolean isAgreed() {
    return agreed;
  }

  public Instant getChangedAt() {
    return changedAt;
  }
}
