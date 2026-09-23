package com.financetracker.loan;

import java.math.BigDecimal;

public record PaymentSplit(
        BigDecimal interest,
        BigDecimal principal,
        BigDecimal extraPrincipal,
        BigDecimal total,
        BigDecimal remaining
) {
}
