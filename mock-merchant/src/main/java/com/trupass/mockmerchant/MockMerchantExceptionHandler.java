package com.trupass.mockmerchant;

import com.trupass.mockmerchant.model.ErrorResponse;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class MockMerchantExceptionHandler {

  @ExceptionHandler(MockMerchantException.class)
  ResponseEntity<ErrorResponse> handle(MockMerchantException e) {
    return ResponseEntity.status(e.status()).body(new ErrorResponse(e.code(), e.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));
    return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_REQUEST", message));
  }
}
