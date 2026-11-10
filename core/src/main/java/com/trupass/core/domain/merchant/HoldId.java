package com.trupass.core.domain.merchant;

public record HoldId(String value) {
  @Override
  public String toString() {
    return value;
  }
}
