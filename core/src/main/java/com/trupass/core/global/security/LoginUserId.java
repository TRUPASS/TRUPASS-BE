package com.trupass.core.global.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인한 사용자의 ID(access token의 {@code sub})를 controller 파라미터로 받는다.
 *
 * <pre>{@code
 * @GetMapping("/me")
 * public ApiResponse<UserResDTO.Me> getMe(@LoginUserId Long userId) { ... }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginUserId {}
