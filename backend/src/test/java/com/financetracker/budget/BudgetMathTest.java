package com.financetracker.budget;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BudgetMathTest {

    @Test
    void underEqualAndOverBudget() {
        BudgetMath.Usage under = BudgetMath.evaluate(new BigDecimal("1000.00"), new BigDecimal("400.00"));
        assertThat(under.remaining()).isEqualByComparingTo("600.00");
        assertThat(under.usagePercent()).isEqualByComparingTo("40.00");
        assertThat(under.overBudget()).isFalse();

        BudgetMath.Usage equal = BudgetMath.evaluate(new BigDecimal("1000.00"), new BigDecimal("1000.00"));
        assertThat(equal.remaining()).isEqualByComparingTo("0.00");
        assertThat(equal.usagePercent()).isEqualByComparingTo("100.00");
        assertThat(equal.overBudget()).isFalse();

        BudgetMath.Usage over = BudgetMath.evaluate(new BigDecimal("1000.00"), new BigDecimal("1500.00"));
        assertThat(over.remaining()).isEqualByComparingTo("-500.00");
        assertThat(over.usagePercent()).isEqualByComparingTo("150.00");
        assertThat(over.overBudget()).isTrue();
    }

    @Test
    void categoriesAndRoundingStayIndependent() {
        BudgetMath.Usage food = BudgetMath.evaluate(new BigDecimal("3000"), new BigDecimal("1"));
        BudgetMath.Usage travel = BudgetMath.evaluate(new BigDecimal("900.00"), new BigDecimal("300.00"));
        assertThat(food.usagePercent()).isEqualByComparingTo("0.03");
        assertThat(travel.usagePercent()).isEqualByComparingTo("33.33");
        assertThat(food.spent()).isEqualByComparingTo("1.00");
    }
}
