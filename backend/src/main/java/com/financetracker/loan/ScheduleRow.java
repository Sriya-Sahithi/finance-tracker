package com.financetracker.loan;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ScheduleRow(
        int paymentNumber,
        LocalDate date,
        BigDecimal openingPrincipal,
        BigDecimal emi,
        BigDecimal interest,
        BigDecimal principal,
        BigDecimal extraPrincipal,
        BigDecimal closingPrincipal
) {
}
