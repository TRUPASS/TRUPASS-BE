package com.trupass.core.adapter.inbound.web;

import com.trupass.core.domain.pkg.OfferCategory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Set;

public record SearchOffersRequest(
    @NotBlank String destination,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    @Min(1) int travelers,
    Set<OfferCategory> categories) {}
