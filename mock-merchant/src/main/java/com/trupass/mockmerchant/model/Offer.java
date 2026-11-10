package com.trupass.mockmerchant.model;

public record Offer(
    String offerId,
    String merchantId,
    Category category,
    String title,
    long amount,
    String currency) {}
