package com.trupass.mockmerchant.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record PayHoldRequest(@Min(0) long amount, @NotBlank String currency) {}
