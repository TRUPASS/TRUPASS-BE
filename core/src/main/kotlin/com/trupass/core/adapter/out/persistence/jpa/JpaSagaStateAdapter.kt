package com.trupass.core.adapter.out.persistence.jpa

import com.trupass.core.application.port.SagaStatePort
import com.trupass.core.domain.checkout.CheckoutId
import com.trupass.core.domain.saga.SagaState
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Postgres(`checkout`, `checkout_step`)에 Saga 상태를 저장하는 어댑터.
 * 엔티티·리포지토리·마이그레이션(V2__...)은 테이블 설계 담당자가 별도 PR로 추가한다.
 */
@Component
class JpaSagaStateAdapter : SagaStatePort {
    override fun save(state: SagaState): SagaState = TODO("담당자 구현")

    override fun load(checkoutId: CheckoutId): SagaState? = TODO("담당자 구현")

    override fun findResumable(
        staleBefore: Instant,
        limit: Int,
    ): List<SagaState> = TODO("담당자 구현")
}
