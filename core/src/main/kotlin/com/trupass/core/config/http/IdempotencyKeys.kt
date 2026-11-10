package com.trupass.core.config.http

import com.trupass.core.domain.checkout.IdempotencyKey
import org.springframework.web.client.RestClient

const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"

/**
 * 요청에 `Idempotency-Key` 헤더를 붙인다.
 *
 * ```
 * restClient.post().uri("/merchants/{id}/holds", id).idempotencyKey(key).body(req).retrieve()
 * ```
 */
fun <S : RestClient.RequestHeadersSpec<S>> S.idempotencyKey(key: IdempotencyKey): S = header(IDEMPOTENCY_KEY_HEADER, key.value)
