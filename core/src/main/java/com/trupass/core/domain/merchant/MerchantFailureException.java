package com.trupass.core.domain.merchant;

public class MerchantFailureException extends RuntimeException {
  private final MerchantFailureReason reason;

  public MerchantFailureException(MerchantFailureReason reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }

  public MerchantFailureException(MerchantFailureReason reason, String message) {
    this(reason, message, null);
  }

  public MerchantFailureReason reason() {
    return reason;
  }
}
