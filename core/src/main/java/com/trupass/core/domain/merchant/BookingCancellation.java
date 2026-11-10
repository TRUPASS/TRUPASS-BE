package com.trupass.core.domain.merchant;

import com.trupass.core.domain.common.Money;
import java.time.Instant;

/**
 * 예약 취소 결과.
 *
 * @param refundedAmount 판매자가 돌려준 금액. 없으면 null.
 */
public record BookingCancellation(BookingId bookingId, Money refundedAmount, Instant cancelledAt) {}
