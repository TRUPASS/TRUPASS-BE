package com.trupass.core.global.apiPayload;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.trupass.core.global.apiPayload.code.BaseErrorCode;
import com.trupass.core.global.apiPayload.code.BaseSuccessCode;
import com.trupass.core.global.apiPayload.code.GeneralSuccessCode;

/**
 * 모든 API의 공통 응답 형식.
 *
 * <pre>{@code
 * {"isSuccess": true, "code": "COMMON200", "message": "성공했습니다.", "result": {...}}
 * }</pre>
 */
@JsonPropertyOrder({"isSuccess", "code", "message", "result"})
public record ApiResponse<T>(
    @JsonProperty("isSuccess") boolean isSuccess,
    String code,
    String message,
    @JsonInclude(JsonInclude.Include.NON_NULL) T result) {

  public static <T> ApiResponse<T> onSuccess(T result) {
    return of(GeneralSuccessCode.OK, result);
  }

  public static <T> ApiResponse<T> of(BaseSuccessCode code, T result) {
    return new ApiResponse<>(true, code.getCode(), code.getMessage(), result);
  }

  public static <T> ApiResponse<T> onFailure(BaseErrorCode code, T result) {
    return new ApiResponse<>(false, code.getCode(), code.getMessage(), result);
  }
}
