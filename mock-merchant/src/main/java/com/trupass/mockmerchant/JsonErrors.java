package com.trupass.mockmerchant;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/** 필터에서 컨트롤러를 거치지 않고 에러 JSON을 바로 쓴다. 메시지는 고정 문자열만 넣으므로 이스케이프하지 않는다. */
final class JsonErrors {
  private JsonErrors() {}

  static void write(HttpServletResponse response, HttpStatus status, String code, String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
  }
}
