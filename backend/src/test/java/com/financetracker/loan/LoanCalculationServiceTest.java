package com.financetracker.loan;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoanCalculationServiceTest {

    private final LoanCalculationService calculator = new LoanCalculationService();

    @Test
    void standardEmiMatchesReducingBalanceFormula() {
        assertThat(calculator.emi(new BigDecimal("100000.00"), new BigDecimal("10"), 12))
                .isEqualByComparingTo("8791.59");
        assertThat(calculator.emi(new BigDecimal("500000"), new BigDecimal("7.5"), 60))
                .isEqualByComparingTo("10018.97");
        assertThat(calculator.emi(new BigDecimal("10000000"), new BigDecimal("9.25"), 240))
                .isEqualByComparingTo("91586.68");
    }

    @Test
    void zeroInterestDividesPrincipalEvenlyAndAdjustsTheFinalInstallment() {
        BigDecimal emi = calculator.emi(new BigDecimal("100.00"), BigDecimal.ZERO, 3);
        assertThat(emi).isEqualByComparingTo("33.33");

        List<ScheduleRow> schedule = calculator.contractualSchedule(
                new BigDecimal("100.00"), BigDecimal.ZERO, emi, LocalDate.of(2026, 1, 5), 3);

        assertThat(schedule).hasSize(3);
        assertThat(schedule.get(0).principal()).isEqualByComparingTo("33.33");
        assertThat(schedule.get(1).principal()).isEqualByComparingTo("33.33");
        assertThat(schedule.get(2).principal()).isEqualByComparingTo("33.34");
        assertThat(schedule.get(2).emi()).isEqualByComparingTo("33.34");
        assertThat(schedule.get(2).interest()).isEqualByComparingTo("0.00");
        assertClosed(schedule, new BigDecimal("100.00"));
    }

    @Test
    void differentRatesTenuresAndLargePrincipalStayNonNegativeAndPayOffExactly() {
        assertClosed(schedule("250000.00", "12.75", 36), new BigDecimal("250000.00"));
        assertClosed(schedule("1000000.00", "8.40", 180), new BigDecimal("1000000.00"));
        assertClosed(schedule("99999999.99", "18.5", 360), new BigDecimal("99999999.99"));
        assertThat(calculator.emi(new BigDecimal("99999999.99"), new BigDecimal("18.5"), 360))
                .isEqualByComparingTo("1547944.53");
    }

    @Test
    void repeatedCallsAreDeterministic() {
        BigDecimal first = calculator.emi(new BigDecimal("100000"), new BigDecimal("10"), 12);
        BigDecimal second = calculator.emi(new BigDecimal("100000.00"), new BigDecimal("10.0000"), 12);
        assertThat(first).isEqualTo(second);
        List<ScheduleRow> a = schedule("100000.00", "10", 12);
        List<ScheduleRow> b = schedule("100000.00", "10", 12);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void prepaymentReducesTenureAndSavesInterest() {
        BigDecimal emi = calculator.emi(new BigDecimal("100000"), new BigDecimal("10"), 12);
        PrepaymentAnalysis analysis = calculator.analyze(
                new BigDecimal("100000.00"),
                new BigDecimal("10"),
                emi,
                LocalDate.of(2026, 2, 5),
                new BigDecimal("10000.00"),
                PrepaymentStrategy.REDUCE_TENURE);

        assertThat(analysis.strategy()).isEqualTo(PrepaymentStrategy.REDUCE_TENURE);
        assertThat(analysis.payment().extraPrincipal()).isEqualByComparingTo("10000.00");
        assertThat(analysis.newOutstanding()).isLessThan(analysis.previousOutstanding());
        assertThat(analysis.newOutstanding()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(analysis.interestSaved()).isGreaterThan(BigDecimal.ZERO);
        assertThat(analysis.emisReduced()).isGreaterThan(0);
        assertThat(analysis.newPayoffDate()).isBefore(analysis.previousPayoffDate());
        assertThat(analysis.newRemainingMonths()).isLessThan(analysis.previousRemainingMonths());
    }

    @Test
    void regularPaymentWithoutExtraDoesNotChangeTheRemainingSchedule() {
        BigDecimal emi = calculator.emi(new BigDecimal("100000"), new BigDecimal("10"), 12);
        PrepaymentAnalysis analysis = calculator.analyze(
                new BigDecimal("100000.00"),
                new BigDecimal("10"),
                emi,
                LocalDate.of(2026, 2, 5),
                BigDecimal.ZERO,
                PrepaymentStrategy.REDUCE_TENURE);
        assertThat(analysis.interestSaved()).isEqualByComparingTo("0.00");
        assertThat(analysis.emisReduced()).isZero();
        assertThat(analysis.payment().extraPrincipal()).isEqualByComparingTo("0.00");
    }

    @Test
    void fullPayoffClearsTheBalanceAndNeverGoesNegative() {
        BigDecimal opening = new BigDecimal("8791.59");
        PrepaymentAnalysis analysis = calculator.analyze(
                opening,
                new BigDecimal("10"),
                new BigDecimal("8791.59"),
                LocalDate.of(2026, 12, 5),
                new BigDecimal("100000.00"),
                PrepaymentStrategy.REDUCE_TENURE);

        assertThat(analysis.newOutstanding()).isEqualByComparingTo("0.00");
        assertThat(analysis.newRemainingMonths()).isZero();
        assertThat(analysis.payment().remaining()).isEqualByComparingTo("0.00");
        assertThat(analysis.payment().principal().add(analysis.payment().extraPrincipal())).isEqualByComparingTo(opening);
        assertThat(analysis.emisReduced()).isGreaterThanOrEqualTo(0);
        assertThat(analysis.newPayoffDate()).isEqualTo(LocalDate.of(2026, 12, 5));
    }

    @Test
    void finalContractualInstallmentAbsorbsRoundingSoTheBalanceIsExactlyZero() {
        List<ScheduleRow> schedule = schedule("100000.00", "10", 12);
        assertThat(schedule).hasSize(12);
        assertThat(schedule.get(schedule.size() - 1).closingPrincipal()).isEqualByComparingTo("0.00");
        assertClosed(schedule, new BigDecimal("100000.00"));
    }

    private List<ScheduleRow> schedule(String principal, String rate, int months) {
        BigDecimal p = new BigDecimal(principal);
        BigDecimal r = new BigDecimal(rate);
        BigDecimal emi = calculator.emi(p, r, months);
        return calculator.contractualSchedule(p, r, emi, LocalDate.of(2026, 1, 5), months);
    }

    private void assertClosed(List<ScheduleRow> schedule, BigDecimal principal) {
        BigDecimal principalPaid = BigDecimal.ZERO;
        for (ScheduleRow row : schedule) {
            assertThat(row.closingPrincipal()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(row.openingPrincipal()).isGreaterThan(BigDecimal.ZERO);
            assertThat(row.principal()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(row.interest()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(row.extraPrincipal()).isEqualByComparingTo("0.00");
            principalPaid = principalPaid.add(row.principal()).add(row.extraPrincipal());
        }
        assertThat(schedule.get(schedule.size() - 1).closingPrincipal()).isEqualByComparingTo("0.00");
        assertThat(principalPaid).isEqualByComparingTo(principal);
    }
}
