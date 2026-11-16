package com.trupass.core.domain.user.service;

import com.trupass.core.domain.user.dto.UserReqDTO;
import com.trupass.core.domain.user.dto.UserResDTO;
import com.trupass.core.domain.user.entity.User;
import com.trupass.core.domain.user.exception.UserErrorCode;
import com.trupass.core.domain.user.repository.UserRepository;
import com.trupass.core.global.apiPayload.exception.GeneralException;
import java.time.Clock;
import java.time.Instant;
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
    return userRepository
        .findById(userId)
        .map(UserResDTO.Me::from)
        .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));
  }
}
