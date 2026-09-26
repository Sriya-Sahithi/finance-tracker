package com.financetracker.loan;

import com.financetracker.common.money.Money;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Reducing-balance EMI math. All money uses scale 2 and HALF_UP.
 * Monthly rate is annual percent / 12 / 100 at scale 16.
 * EMI = P × r × (1+r)^n / ((1+r)^n − 1). Zero-interest loans use P / n.
 */
@Service
public class LoanCalculationService {

    private static final int RATE_SCALE = 16;
    private static final MathContext POW = new MathContext(34, RoundingMode.HALF_UP);
    private static final int MAX_PROJECTION = 1200;

    public BigDecimal monthlyRate(BigDecimal annualPercent) {
        if (annualPercent.signum() < 0) {
            throw new IllegalArgumentException("Interest rate cannot be negative");
        }
        return annualPercent
                .divide(BigDecimal.valueOf(100), RATE_SCALE, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(12), RATE_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal emi(BigDecimal principal, BigDecimal annualPercent, int months) {
        BigDecimal p = Money.scale(principal);
        if (months < 1) {
            throw new IllegalArgumentException("Tenure must be at least 1 month");
        }
        if (p.signum() <= 0) {
            throw new IllegalArgumentException("Principal must be positive");
        }
        BigDecimal rate = monthlyRate(annualPercent);
        if (rate.signum() == 0) {
            return p.divide(BigDecimal.valueOf(months), Money.SCALE, Money.ROUNDING);
        }
        BigDecimal growth = BigDecimal.ONE.add(rate).pow(months, POW);
        BigDecimal numerator = p.multiply(rate, POW).multiply(growth, POW);
        BigDecimal denominator = growth.subtract(BigDecimal.ONE, POW);
        return numerator.divide(denominator, Money.SCALE, Money.ROUNDING);
    }

    public BigDecimal interestDue(BigDecimal openingPrincipal, BigDecimal annualPercent) {
        return Money.scale(Money.scale(openingPrincipal).multiply(monthlyRate(annualPercent)));
    }

    public List<ScheduleRow> contractualSchedule(
            BigDecimal principal,
            BigDecimal annualPercent,
            BigDecimal emi,
            LocalDate firstPaymentDate,
            int months
    ) {
        return buildSchedule(principal, annualPercent, emi, firstPaymentDate, months, true);
    }

    public List<ScheduleRow> project(BigDecimal openingPrincipal, BigDecimal annualPercent, BigDecimal emi, LocalDate nextPaymentDate) {
        if (Money.scale(openingPrincipal).signum() == 0) {
            return List.of();
        }
        return buildSchedule(openingPrincipal, annualPercent, emi, nextPaymentDate, MAX_PROJECTION, false);
    }

    public PaymentSplit split(BigDecimal openingPrincipal, BigDecimal annualPercent, BigDecimal emi, BigDecimal extraPrincipal) {
        BigDecimal opening = Money.scale(openingPrincipal);
        if (opening.signum() <= 0) {
            throw new IllegalArgumentException("Loan is already paid off");
        }
        BigDecimal extraRequested = extraPrincipal == null ? Money.ZERO : Money.scale(extraPrincipal);
        if (extraRequested.signum() < 0) {
            throw new IllegalArgumentException("Extra principal cannot be negative");
        }
        BigDecimal interest = interestDue(opening, annualPercent);
        BigDecimal regular = Money.scale(emi).subtract(interest);
        if (regular.signum() < 0) {
            throw new IllegalArgumentException("EMI does not cover the interest due");
        }
        if (regular.compareTo(opening) > 0) {
            regular = opening;
        }
        BigDecimal extraApplied = extraRequested.min(opening.subtract(regular));
        extraApplied = Money.scale(extraApplied);
        BigDecimal remaining = Money.scale(opening.subtract(regular).subtract(extraApplied));
        BigDecimal total = Money.scale(interest.add(regular).add(extraApplied));
        return new PaymentSplit(interest, regular, extraApplied, total, remaining);
    }

    public PrepaymentAnalysis analyze(
            BigDecimal outstanding,
            BigDecimal annualPercent,
            BigDecimal emi,
            LocalDate nextPaymentDate,
            BigDecimal extraPrincipal,
            PrepaymentStrategy strategy
    ) {
        return analyze(outstanding, annualPercent, emi, nextPaymentDate, extraPrincipal, strategy, null);
    }

    public PrepaymentAnalysis analyze(
            BigDecimal outstanding,
            BigDecimal annualPercent,
            BigDecimal emi,
            LocalDate nextPaymentDate,
            BigDecimal extraPrincipal,
            PrepaymentStrategy strategy,
            Integer remainingMonths
    ) {
        if (strategy == null) {
            strategy = PrepaymentStrategy.REDUCE_TENURE;
        }
        return switch (strategy) {
            case REDUCE_TENURE -> analyzeReduceTenure(outstanding, annualPercent, emi, nextPaymentDate, extraPrincipal);
            case REDUCE_EMI -> analyzeReduceEmi(outstanding, annualPercent, emi, nextPaymentDate, extraPrincipal, remainingMonths);
        };
    }

    private PrepaymentAnalysis analyzeReduceTenure(
            BigDecimal outstanding,
            BigDecimal annualPercent,
            BigDecimal emi,
            LocalDate nextPaymentDate,
            BigDecimal extraPrincipal
    ) {
        List<ScheduleRow> before = project(outstanding, annualPercent, emi, nextPaymentDate);
        PaymentSplit payment = split(outstanding, annualPercent, emi, extraPrincipal);
        List<ScheduleRow> after = payment.remaining().signum() == 0
                ? List.of()
                : project(payment.remaining(), annualPercent, emi, nextPaymentDate.plusMonths(1));
        BigDecimal oldInterest = sumInterest(before);
        BigDecimal newInterest = payment.interest().add(sumInterest(after));
        BigDecimal saved = Money.scale(oldInterest.subtract(newInterest));
        int previousRemaining = before.size();
        int newRemaining = after.size();
        LocalDate previousPayoff = before.isEmpty() ? nextPaymentDate : before.get(before.size() - 1).date();
        LocalDate newPayoff = payment.remaining().signum() == 0
                ? nextPaymentDate
                : after.get(after.size() - 1).date();
        return new PrepaymentAnalysis(
                Money.scale(outstanding),
                payment.remaining(),
                saved,
                previousPayoff,
                newPayoff,
                previousRemaining - (1 + newRemaining),
                previousRemaining,
                newRemaining,
                PrepaymentStrategy.REDUCE_TENURE,
                payment);
    }

    private PrepaymentAnalysis analyzeReduceEmi(
            BigDecimal outstanding,
            BigDecimal annualPercent,
            BigDecimal emi,
            LocalDate nextPaymentDate,
            BigDecimal extraPrincipal,
            Integer remainingMonths
    ) {
        if (remainingMonths == null || remainingMonths < 1) {
            throw new IllegalArgumentException("Remaining months are required when reducing the EMI");
        }
        List<ScheduleRow> before = project(outstanding, annualPercent, emi, nextPaymentDate);
        PaymentSplit payment = split(outstanding, annualPercent, emi, extraPrincipal);
        BigDecimal newEmi = emi(payment.remaining(), annualPercent, remainingMonths);
        List<ScheduleRow> after = payment.remaining().signum() == 0
                ? List.of()
                : project(payment.remaining(), annualPercent, newEmi, nextPaymentDate.plusMonths(1));
        BigDecimal oldInterest = sumInterest(before);
        BigDecimal newInterest = payment.interest().add(sumInterest(after));
        BigDecimal saved = Money.scale(oldInterest.subtract(newInterest));
        LocalDate previousPayoff = before.isEmpty() ? nextPaymentDate : before.get(before.size() - 1).date();
        LocalDate newPayoff = payment.remaining().signum() == 0
                ? nextPaymentDate
                : nextPaymentDate.plusMonths(remainingMonths - 1L);
        return new PrepaymentAnalysis(
                Money.scale(outstanding),
                payment.remaining(),
                saved,
                previousPayoff,
                newPayoff,
                0,
                before.size(),
                remainingMonths,
                PrepaymentStrategy.REDUCE_EMI,
                payment);
    }

    private List<ScheduleRow> buildSchedule(
            BigDecimal principal,
            BigDecimal annualPercent,
            BigDecimal emiAmount,
            LocalDate firstPaymentDate,
            int maxRows,
            boolean forceCloseOnLast
    ) {
        BigDecimal opening = Money.scale(principal);
        BigDecimal installment = Money.scale(emiAmount);
        if (installment.signum() <= 0) {
            throw new IllegalArgumentException("EMI must be positive");
        }
        List<ScheduleRow> rows = new ArrayList<>();
        for (int i = 1; i <= maxRows && opening.signum() > 0; i++) {
            BigDecimal interest = interestDue(opening, annualPercent);
            boolean lastSlot = forceCloseOnLast && i == maxRows;
            BigDecimal regular = installment.subtract(interest);
            BigDecimal principalPart;
            BigDecimal payment;
            if (lastSlot || regular.compareTo(opening) >= 0) {
                if (!lastSlot && regular.signum() < 0) {
                    throw new IllegalArgumentException("EMI does not cover the interest due");
                }
                principalPart = opening;
                payment = Money.scale(opening.add(interest));
            } else {
                if (regular.signum() <= 0) {
                    throw new IllegalArgumentException("EMI does not cover the interest due");
                }
                principalPart = regular;
                payment = installment;
            }
            BigDecimal closing = Money.scale(opening.subtract(principalPart));
            if (closing.signum() < 0) {
                throw new IllegalStateException("Closing principal became negative");
            }
            rows.add(new ScheduleRow(
                    i,
                    firstPaymentDate.plusMonths(i - 1L),
                    opening,
                    payment,
                    interest,
                    principalPart,
                    Money.ZERO,
                    closing));
            opening = closing;
        }
        if (opening.signum() > 0) {
            throw new IllegalStateException("Schedule did not reach a zero balance");
        }
        return rows;
    }

    private BigDecimal sumInterest(List<ScheduleRow> rows) {
        BigDecimal total = Money.ZERO;
        for (ScheduleRow row : rows) {
            total = total.add(row.interest());
        }
        return Money.scale(total);
    }
}
