package com.trupass.core.domain.auth.repository;

import com.trupass.core.domain.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /**
   * {@code SELECT ... FOR UPDATE}로 조회한다. 같은 토큰을 동시에 조회한 다른 트랜잭션은 이 트랜잭션이 끝날 때까지 기다렸다가 커밋된 값을 읽는다.
   * 트랜잭션 안에서만 쓸 수 있다.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from RefreshToken t where t.tokenHash = :tokenHash")
  Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

  @Modifying
  @Query(
      "update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
  int revokeAllByUserId(@Param("userId") Long userId, @Param("now") Instant now);
}
