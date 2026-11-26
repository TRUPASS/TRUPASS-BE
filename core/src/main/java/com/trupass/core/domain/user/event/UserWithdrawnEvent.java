package com.trupass.core.domain.user.event;

/**
 * 회원이 탈퇴했다. 탈퇴와 같은 트랜잭션 안에서 동기로 발행한다. 다른 도메인은 이 이벤트를 받아 자기 데이터를 정리한다(예: auth가 refresh token을 모두
 * 폐기). user 도메인이 다른 도메인을 직접 부르지 않으려고 이벤트로 알린다.
 */
public record UserWithdrawnEvent(Long userId) {}
