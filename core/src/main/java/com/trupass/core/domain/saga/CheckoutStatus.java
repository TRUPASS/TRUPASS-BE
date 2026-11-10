package com.trupass.core.domain.saga;

/** 체크아웃 전체 상태. 자동 보상이 1회 재시도 후에도 실패하면 {@link #NEEDS_MANUAL_REVIEW}에서 멈춘다. */
public enum CheckoutStatus {
  IN_PROGRESS,
  COMPLETED,
  COMPENSATING,
  COMPENSATED,
  NEEDS_MANUAL_REVIEW
}
