package com.trupass.core.application.port

import com.trupass.core.domain.checkout.CheckoutId
import com.trupass.core.domain.saga.SagaState
import java.time.Instant

/**
 * Saga 진행 상태를 저장·재개하는 포트. 상태는 메모리가 아니라 영속 저장소에 둔다.
 *
 * 어댑터: 지금은 Postgres(`checkout`, `checkout_step` 테이블), 나중에 Temporal.
 */
interface SagaStatePort {
    /** 상태를 저장한다. [SagaState.version]이 저장소 값과 다르면 낙관적 락 충돌로 실패한다. */
    fun save(state: SagaState): SagaState

    /** 체크아웃 상태를 읽는다. 없으면 null. */
    fun load(checkoutId: CheckoutId): SagaState?

    /** 서버 재시작 등으로 [staleBefore] 이전부터 멈춰 있는, 재개해야 할 체크아웃을 찾는다. */
    fun findResumable(
        staleBefore: Instant,
        limit: Int,
    ): List<SagaState>
}
