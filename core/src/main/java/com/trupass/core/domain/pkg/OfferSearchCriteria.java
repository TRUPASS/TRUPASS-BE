package com.trupass.core.domain.pkg;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

public record OfferSearchCriteria(
    String destination,
    LocalDate startDate,
    LocalDate endDate,
    int travelers,
    Set<OfferCategory> categories) {
  public OfferSearchCriteria {
    categories =
        categories == null || categories.isEmpty()
            ? Set.copyOf(EnumSet.allOf(OfferCategory.class))
            : Set.copyOf(categories);
  }
}
