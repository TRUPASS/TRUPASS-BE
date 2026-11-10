package com.trupass.core.domain.merchant;

public record BookingId(String value) {
  @Override
  public String toString() {
    return value;
  }
}
