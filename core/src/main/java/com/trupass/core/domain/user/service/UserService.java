package com.trupass.core.domain.user.service;

import com.trupass.core.domain.user.dto.UserReqDTO;
import com.trupass.core.domain.user.dto.UserResDTO;
import com.trupass.core.domain.user.entity.User;
import com.trupass.core.domain.user.exception.UserErrorCode;
import com.trupass.core.domain.user.repository.UserRepository;
import com.trupass.core.global.apiPayload.exception.GeneralException;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 조회·가입. 다른 도메인(auth 등)은 이 service로만 사용자를 다룬다. */
@Service
public class UserService {
  private final UserRepository userRepository;
  private final Clock clock;

  public UserService(UserRepository userRepository, Clock clock) {
    this.userRepository = userRepository;
    this.clock = clock;
  }

  /** Google 계정({@code sub})으로 찾고, 없으면 가입시킨다. 있으면 프로필을 최신으로 맞춘다. */
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
    return UserResDTO.Me.from(findUser(userId));
  }

  /** 회원 정보 입력·수정. 처음 저장하면 회원 정보 입력을 마친 것으로 기록한다. 같은 값을 다시 보내도 결과가 같다. */
  @Transactional
  public UserResDTO.Me updateProfile(Long userId, UserReqDTO.Profile request) {
    if (request.birthYear() > Year.now(clock).getValue()) {
      throw new GeneralException(UserErrorCode.INVALID_BIRTH_YEAR);
    }
    User user = findUser(userId);
    user.updateProfile(
        request.nickname(),
        request.birthYear(),
        request.gender(),
        request.interests(),
        request.agreements().agreedTerms(),
        clock.instant());
    return UserResDTO.Me.from(user);
  }

  private User findUser(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));
  }
}
