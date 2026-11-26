package com.trupass.core.domain.user.service;

import com.trupass.core.domain.user.dto.UserReqDTO;
import com.trupass.core.domain.user.dto.UserResDTO;
import com.trupass.core.domain.user.entity.AgreementHistory;
import com.trupass.core.domain.user.entity.User;
import com.trupass.core.domain.user.event.UserWithdrawnEvent;
import com.trupass.core.domain.user.exception.UserErrorCode;
import com.trupass.core.domain.user.repository.AgreementHistoryRepository;
import com.trupass.core.domain.user.repository.UserRepository;
import com.trupass.core.global.apiPayload.exception.GeneralException;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 조회·가입·회원 정보·탈퇴. 다른 도메인(auth, trip 등)은 이 service로만 사용자를 다룬다. */
@Service
public class UserService {
  private final UserRepository userRepository;
  private final AgreementHistoryRepository agreementHistoryRepository;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public UserService(
      UserRepository userRepository,
      AgreementHistoryRepository agreementHistoryRepository,
      ApplicationEventPublisher events,
      Clock clock) {
    this.userRepository = userRepository;
    this.agreementHistoryRepository = agreementHistoryRepository;
    this.events = events;
    this.clock = clock;
  }

  /**
   * Google 계정({@code sub})으로 찾고, 없으면 가입시킨다. 있으면 프로필을 최신으로 맞춘다. 탈퇴한 회원은 Google 계정과의 연결이 끊겨 있어 새로
   * 가입된다.
   */
  @Transactional
  public UserResDTO.GoogleLogin loginWithGoogle(UserReqDTO.GoogleProfile profile) {
    Instant now = clock.instant();
    Optional<User> existing = userRepository.findByGoogleSub(profile.sub());
    if (existing.isPresent()) {
      User user = existing.get();
      user.recordGoogleLogin(profile.email(), profile.name(), profile.pictureUrl(), now);
      return new UserResDTO.GoogleLogin(UserResDTO.Me.from(user), false);
    }
    User user =
        userRepository.save(
            User.fromGoogle(
                profile.sub(), profile.email(), profile.name(), profile.pictureUrl(), now));
    return new UserResDTO.GoogleLogin(UserResDTO.Me.from(user), true);
  }

  @Transactional(readOnly = true)
  public UserResDTO.Me getMe(Long userId) {
    return UserResDTO.Me.from(findActiveUser(userId));
  }

  /**
   * 회원 정보 입력·수정. 처음 저장하면 회원 정보 입력을 마친 것으로 기록한다. 같은 값을 다시 보내도 결과가 같다. 약관 동의가 바뀌었으면(새로 동의, 선택 약관 해제)
   * 이력에 남긴다.
   */
  @Transactional
  public UserResDTO.Me updateProfile(Long userId, UserReqDTO.Profile request) {
    if (request.birthYear() > Year.now(clock).getValue()) {
      throw new GeneralException(UserErrorCode.INVALID_BIRTH_YEAR);
    }
    User user = findActiveUser(userId);
    Instant now = clock.instant();
    List<User.AgreementChange> changes =
        user.updateProfile(
            request.nickname(),
            request.birthYear(),
            request.gender(),
            request.interests(),
            request.agreements().agreedTerms(),
            now);
    recordAgreementChanges(userId, changes, now);
    return UserResDTO.Me.from(user);
  }

  /**
   * 회원 탈퇴. 개인정보를 지우고 약관 동의를 모두 거둔 것으로 이력에 남긴 뒤 {@link UserWithdrawnEvent}를 발행한다(같은 트랜잭션에서 auth가
   * refresh token을 모두 폐기한다). 이미 발급한 access token은 만료(30분)까지 남지만, 탈퇴한 회원으로 오는 요청은 {@code USER401_1}로
   * 거절한다.
   */
  @Transactional
  public void withdraw(Long userId) {
    User user = findActiveUser(userId);
    Instant now = clock.instant();
    List<User.AgreementChange> changes = user.withdraw(now);
    recordAgreementChanges(userId, changes, now);
    events.publishEvent(new UserWithdrawnEvent(userId));
  }

  /**
   * 추천(trip → AI agent)에 쓰는 회원 성향. 출생 연도 대신 연령대(10살 단위, 연 나이 기준)만 넘긴다. 회원 정보 입력 전이면 연령대는 null, 관심사는
   * 빈 목록이다.
   */
  @Transactional(readOnly = true)
  public UserResDTO.TravelPreference getTravelPreference(Long userId) {
    User user = findActiveUser(userId);
    Integer ageGroup =
        user.getBirthYear() == null
            ? null
            : ageGroup(Year.now(clock).getValue() - user.getBirthYear());
    return new UserResDTO.TravelPreference(
        ageGroup, user.getInterests().stream().sorted().toList());
  }

  /** 나이를 10살 단위로 내린다. 34 → 30, 9 → 0 */
  static int ageGroup(int age) {
    return Math.max(0, age) / 10 * 10;
  }

  private void recordAgreementChanges(
      Long userId, List<User.AgreementChange> changes, Instant now) {
    agreementHistoryRepository.saveAll(
        changes.stream()
            .map(c -> new AgreementHistory(userId, c.type(), c.agreed(), now))
            .toList());
  }

  /** 탈퇴하지 않은 회원. 없으면 USER404_1, 탈퇴했으면 USER401_1(FE는 401을 받으면 로그인을 다시 확인한다). */
  private User findActiveUser(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));
    if (user.isWithdrawn()) {
      throw new GeneralException(UserErrorCode.WITHDRAWN_USER);
    }
    return user;
  }
}
