package com.trupass.core.domain.auth.event;

import java.time.Instant;

/**
 * 이 사용자의 access token 중 {@code at} 이하에 발급된 것을 폐기한다. 로그아웃 · refresh token 재사용 감지 · 회원 탈퇴 때 refresh
 * token 폐기와 같은 트랜잭션에서 발행하고, 커밋된 뒤에 Redis에 기록한다({@code AccessTokenRevocationListener}).
 */
public record AccessTokenRevokedEvent(Long userId, Instant at) {}
