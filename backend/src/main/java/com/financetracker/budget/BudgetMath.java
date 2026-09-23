package com.financetracker.budget;

import com.financetracker.common.money.Money;
import java.math.BigDecimal;

public final class BudgetMath {

    private BudgetMath() {
    }

    public static Usage evaluate(BigDecimal budget, BigDecimal spent) {
        if (budget == null) {
            return new Usage(null, Money.scale(spent), null, null, false);
        }
        BigDecimal scaledBudget = Money.scale(budget);
        BigDecimal scaledSpent = Money.scale(spent);
        if (scaledBudget.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Budget amount must be positive");
        }
        BigDecimal remaining = Money.scale(scaledBudget.subtract(scaledSpent));
        BigDecimal percent = scaledSpent.multiply(BigDecimal.valueOf(100))
                .divide(scaledBudget, Money.SCALE, Money.ROUNDING);
        boolean over = scaledSpent.compareTo(scaledBudget) > 0;
        return new Usage(scaledBudget, scaledSpent, remaining, percent, over);
    }

    public record Usage(
            BigDecimal budget,
            BigDecimal spent,
            BigDecimal remaining,
            BigDecimal usagePercent,
            boolean overBudget
    ) {
    }
}
