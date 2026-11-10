package com.trupass.core.domain.pkg;

public record OfferId(String value) {
  @Override
  public String toString() {
    return value;
  }
}
