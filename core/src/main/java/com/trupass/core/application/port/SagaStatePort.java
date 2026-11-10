package com.trupass.core.application.port;

import com.trupass.core.domain.checkout.CheckoutId;
import com.trupass.core.domain.saga.SagaState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Saga 진행 상태를 저장·재개하는 포트. 상태는 메모리가 아니라 영속 저장소에 둔다.
 *
 * <p>어댑터: 지금은 Postgres({@code checkout}, {@code checkout_step} 테이블), 나중에 Temporal.
 */
public interface SagaStatePort {

  /** 상태를 저장한다. {@link SagaState#version()}이 저장소 값과 다르면 낙관적 락 충돌로 실패한다. */
  SagaState save(SagaState state);

  /** 체크아웃 상태를 읽는다. */
  Optional<SagaState> load(CheckoutId checkoutId);

  /** 서버 재시작 등으로 {@code staleBefore} 이전부터 멈춰 있는, 재개해야 할 체크아웃을 찾는다. */
  List<SagaState> findResumable(Instant staleBefore, int limit);
}
