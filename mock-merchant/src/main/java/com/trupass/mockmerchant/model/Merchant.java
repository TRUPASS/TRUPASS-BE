package com.trupass.mockmerchant.model;

/** 판매자 3곳: A=항공, B=숙소, C=체험. */
public record Merchant(String merchantId, String name, Category category) {}
