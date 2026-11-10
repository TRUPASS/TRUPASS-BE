package com.trupass.mockmerchant;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 테스트에서 시간을 앞으로 돌리기 위한 시계. */
class MutableClock extends Clock {
  private volatile Instant instant = Instant.EPOCH;

  void set(Instant instant) {
    this.instant = instant;
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }

  @Override
  public Instant instant() {
    return instant;
  }
}
