package com.trupass.core.global.apiPayload.exception;

import com.trupass.core.global.apiPayload.code.BaseErrorCode;

/**
 * 비즈니스 예외. 도메인 에러 코드를 담아 던지면 {@code GlobalExceptionHandler}가 {@code ApiResponse}로 바꾼다.
 *
 * <pre>{@code
 * throw new GeneralException(MerchantErrorCode.SOLD_OUT);
 * }</pre>
 */
public class GeneralException extends RuntimeException {
  private final BaseErrorCode errorCode;

  public GeneralException(BaseErrorCode errorCode) {
    this(errorCode, null);
  }

  public GeneralException(BaseErrorCode errorCode, Throwable cause) {
    super(errorCode.getMessage(), cause);
    this.errorCode = errorCode;
  }

  public BaseErrorCode getErrorCode() {
    return errorCode;
  }
}
