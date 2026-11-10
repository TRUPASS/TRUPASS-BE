package com.trupass.core.domain.saga;

public enum SagaStepStatus {
  PENDING,
  SUCCEEDED,
  FAILED,
  COMPENSATED,
  COMPENSATION_FAILED
}
