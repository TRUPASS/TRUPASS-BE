package com.trupass.core.domain.auth.service;

import com.trupass.core.domain.auth.event.AccessTokenRevokedEvent;
import com.trupass.core.global.security.AccessTokenRevocationStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * refresh token 폐기가 커밋된 뒤에 access token 폐기 기준 시각을 Redis에 쓴다.
 *
 * <p>트랜잭션 안에서 바로 쓰면 Redis 쪽에서 예외(GeneralException이 아니다)가 하나라도 새어 나올 때 트랜잭션이 롤백돼, {@code consume}의
 * 재사용 감지처럼 예외를 던지면서도 커밋돼야 하는 DB 폐기까지 취소된다. store가 DataAccessException을 삼키긴 하지만, DB 폐기가 그 구현에 기대지 않게
 * 커밋 뒤로 뺐다. 커밋 뒤라서 Redis가 응답하지 않아도 폐기한 행의 잠금을 붙잡고 기다리지 않는다.
 *
 * <p>{@link TransactionalEventListener}는 트랜잭션 밖에서 발행된 이벤트를 조용히 버린다. 발행하는 메서드는 모두 {@code
 * Transactional}이어야 한다.
 */
@Component
public class AccessTokenRevocationListener {
  private final AccessTokenRevocationStore store;

  public AccessTokenRevocationListener(AccessTokenRevocationStore store) {
    this.store = store;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(AccessTokenRevokedEvent event) {
    store.revokeBefore(event.userId(), event.at());
  }
}
