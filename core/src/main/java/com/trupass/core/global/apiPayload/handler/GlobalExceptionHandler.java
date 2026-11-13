package com.trupass.core.global.apiPayload.handler;

import com.trupass.core.global.apiPayload.ApiResponse;
import com.trupass.core.global.apiPayload.code.BaseErrorCode;
import com.trupass.core.global.apiPayload.code.GeneralErrorCode;
import com.trupass.core.global.apiPayload.exception.GeneralException;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** 모든 예외를 {@link ApiResponse} 형식으로 바꿔 내려준다. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(GeneralException.class)
  public ResponseEntity<ApiResponse<Void>> handleGeneral(GeneralException e) {
    BaseErrorCode code = e.getErrorCode();
    if (code.getStatus().is5xxServerError()) {
      log.error("{}: {}", code.getCode(), e.getMessage(), e);
    }
    return toResponse(code, null);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolation(
      ConstraintViolationException e) {
    Map<String, String> errors = new LinkedHashMap<>();
    e.getConstraintViolations()
        .forEach(v -> errors.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
    return toResponse(GeneralErrorCode.INVALID_INPUT, errors);
  }

  /** 아직 구현하지 않은 client 자리(UnsupportedOperationException)는 500이 아니라 501로 알린다. */
  @ExceptionHandler(UnsupportedOperationException.class)
  public ResponseEntity<ApiResponse<String>> handleNotImplemented(UnsupportedOperationException e) {
    return toResponse(GeneralErrorCode.NOT_IMPLEMENTED, e.getMessage());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
    log.error("Unhandled exception", e);
    return toResponse(GeneralErrorCode.INTERNAL_SERVER_ERROR, null);
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException e,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    Map<String, String> errors = new LinkedHashMap<>();
    for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
      errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
    }
    BaseErrorCode code = GeneralErrorCode.INVALID_INPUT;
    return ResponseEntity.status(code.getStatus()).body(ApiResponse.onFailure(code, errors));
  }

  /** Spring MVC 표준 예외(404, 405, 잘못된 JSON 등)도 같은 형식으로 맞춘다. */
  @Override
  protected @Nullable ResponseEntity<Object> handleExceptionInternal(
      Exception e,
      @Nullable Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    BaseErrorCode code = fromStatus(statusCode);
    return ResponseEntity.status(statusCode)
        .headers(headers)
        .body(ApiResponse.onFailure(code, null));
  }

  private static BaseErrorCode fromStatus(HttpStatusCode statusCode) {
    HttpStatus status = HttpStatus.resolve(statusCode.value());
    if (status == null) {
      return GeneralErrorCode.INTERNAL_SERVER_ERROR;
    }
    return switch (status) {
      case NOT_FOUND -> GeneralErrorCode.NOT_FOUND;
      case METHOD_NOT_ALLOWED -> GeneralErrorCode.METHOD_NOT_ALLOWED;
      case UNAUTHORIZED -> GeneralErrorCode.UNAUTHORIZED;
      case FORBIDDEN -> GeneralErrorCode.FORBIDDEN;
      case CONFLICT -> GeneralErrorCode.CONFLICT;
      case NOT_IMPLEMENTED -> GeneralErrorCode.NOT_IMPLEMENTED;
      default ->
          status.is4xxClientError()
              ? GeneralErrorCode.BAD_REQUEST
              : GeneralErrorCode.INTERNAL_SERVER_ERROR;
    };
  }

  private static <T> ResponseEntity<ApiResponse<T>> toResponse(BaseErrorCode code, T result) {
    return ResponseEntity.status(code.getStatus()).body(ApiResponse.onFailure(code, result));
  }
}
