package com.trupass.core.global.security;

import com.trupass.core.global.apiPayload.ApiResponse;
import com.trupass.core.global.apiPayload.code.BaseErrorCode;
import com.trupass.core.global.apiPayload.code.GeneralErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Spring Security 필터에서 막힌 요청(401·403)도 {@link ApiResponse} 형식으로 내려준다. */
@Component
public class ApiAuthenticationHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {
  private final JsonMapper jsonMapper;

  public ApiAuthenticationHandlers(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  /** 토큰이 없으면 {@code COMMON401}, 있는데 틀렸거나 만료됐으면 {@code COMMON401_1}. */
  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    BaseErrorCode code =
        authException instanceof InvalidBearerTokenException
            ? GeneralErrorCode.INVALID_TOKEN
            : GeneralErrorCode.UNAUTHORIZED;
    write(response, code);
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    write(response, GeneralErrorCode.FORBIDDEN);
  }

  private void write(HttpServletResponse response, BaseErrorCode code) throws IOException {
    response.setStatus(code.getStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    jsonMapper.writeValue(response.getOutputStream(), ApiResponse.onFailure(code, null));
  }
}
