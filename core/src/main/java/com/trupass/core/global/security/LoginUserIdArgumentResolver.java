package com.trupass.core.global.security;

import com.trupass.core.global.apiPayload.code.GeneralErrorCode;
import com.trupass.core.global.apiPayload.exception.GeneralException;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** {@link LoginUserId}를 채운다. 인증되지 않은 요청이면 {@code COMMON401}. */
public class LoginUserIdArgumentResolver implements HandlerMethodArgumentResolver {

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.hasParameterAnnotation(LoginUserId.class)
        && Long.class.equals(parameter.getParameterType());
  }

  @Override
  public Long resolveArgument(
      MethodParameter parameter,
      @Nullable ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      @Nullable WebDataBinderFactory binderFactory) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
      try {
        return Long.valueOf(jwt.getSubject());
      } catch (NumberFormatException e) {
        throw new GeneralException(GeneralErrorCode.INVALID_TOKEN, e);
      }
    }
    throw new GeneralException(GeneralErrorCode.UNAUTHORIZED);
  }
}
