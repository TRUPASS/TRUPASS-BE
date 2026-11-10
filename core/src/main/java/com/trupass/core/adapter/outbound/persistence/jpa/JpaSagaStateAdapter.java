package com.trupass.core.adapter.outbound.persistence.jpa;

import com.trupass.core.application.port.SagaStatePort;
import com.trupass.core.domain.checkout.CheckoutId;
import com.trupass.core.domain.saga.SagaState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Postgres({@code checkout}, {@code checkout_step})에 Saga 상태를 저장하는 어댑터. 엔티티·리포지토리·마이그레이션(V2__...)은
 * 테이블 설계 담당자가 별도 PR로 추가한다.
 */
@Component
public class JpaSagaStateAdapter implements SagaStatePort {

  @Override
  public SagaState save(SagaState state) {
    throw new UnsupportedOperationException("담당자 구현");
  }

  @Override
  public Optional<SagaState> load(CheckoutId checkoutId) {
    throw new UnsupportedOperationException("담당자 구현");
  }

  @Override
  public List<SagaState> findResumable(Instant staleBefore, int limit) {
    throw new UnsupportedOperationException("담당자 구현");
  }
}
