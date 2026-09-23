package com.financetracker.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private Money() {
    }

    public static BigDecimal scale(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        return value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? ZERO : scale(value);
    }
}
