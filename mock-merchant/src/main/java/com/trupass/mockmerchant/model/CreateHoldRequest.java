package com.trupass.mockmerchant.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateHoldRequest(@NotBlank String offerId, @Min(1) Integer quantity) {
  public CreateHoldRequest {
    if (quantity == null) {
      quantity = 1;
    }
  }
}
