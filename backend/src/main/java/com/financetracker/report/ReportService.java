package com.financetracker.report;

import com.financetracker.budget.BudgetService;
import com.financetracker.budget.dto.BudgetResponse;
import com.financetracker.category.Category;
import com.financetracker.category.CategoryRepository;
import com.financetracker.common.exception.BadRequestException;
import com.financetracker.common.money.Money;
import com.financetracker.common.security.CurrentUserService;
import com.financetracker.loan.Loan;
import com.financetracker.loan.LoanCalculationService;
import com.financetracker.loan.LoanPayment;
import com.financetracker.loan.LoanPaymentRepository;
import com.financetracker.loan.LoanRepository;
import com.financetracker.loan.PaymentSplit;
import com.financetracker.report.dto.MonthlyReportResponse;
import com.financetracker.transaction.TransactionRepository;
import com.financetracker.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final BudgetService budgetService;
    private final LoanRepository loanRepository;
    private final LoanPaymentRepository loanPaymentRepository;
    private final LoanCalculationService loanCalculationService;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public ReportService(
            TransactionRepository transactionRepository,
            CategoryRepository categoryRepository,
            BudgetService budgetService,
            LoanRepository loanRepository,
            LoanPaymentRepository loanPaymentRepository,
            LoanCalculationService loanCalculationService,
            CurrentUserService currentUserService,
            Clock clock
    ) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.budgetService = budgetService;
        this.loanRepository = loanRepository;
        this.loanPaymentRepository = loanPaymentRepository;
        this.loanCalculationService = loanCalculationService;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MonthlyReportResponse monthly(Integer year, Integer month) {
        if ((year == null) != (month == null)) {
            throw new BadRequestException("Provide both year and month");
        }
        YearMonth selected = year == null ? YearMonth.now(clock) : YearMonth.of(year, month);
        Long userId = currentUserService.requireId();
        YearMonth today = YearMonth.now(clock);
        LocalDate yearStart = LocalDate.of(selected.getYear(), 1, 1);
        LocalDate yearEnd = yearStart.plusYears(1);

        Map<Integer, BigDecimal> income = new HashMap<>();
        Map<Integer, BigDecimal> expenses = new HashMap<>();
        for (Object[] row : transactionRepository.sumMonthlyCashflow(userId, yearStart, yearEnd)) {
            int rowMonth = ((Number) row[0]).intValue();
            TransactionType type = (TransactionType) row[1];
            BigDecimal amount = money(row[2]);
            if (type == TransactionType.INCOME) {
                income.merge(rowMonth, amount, BigDecimal::add);
            } else if (type == TransactionType.EXPENSE) {
                expenses.merge(rowMonth, amount, BigDecimal::add);
            }
        }

        List<MonthlyReportResponse.CashFlowPoint> cashFlow = new ArrayList<>();
        List<MonthlyReportResponse.MonthAmount> spending = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            BigDecimal in = money(income.get(m));
            BigDecimal out = money(expenses.get(m));
            cashFlow.add(new MonthlyReportResponse.CashFlowPoint(selected.getYear(), m, in, out, Money.scale(in.subtract(out))));
            spending.add(new MonthlyReportResponse.MonthAmount(selected.getYear(), m, out));
        }

        Map<Long, String> categoryNames = new HashMap<>();
        for (Category category : categoryRepository.findByUserIdOrderByTypeAscNameAsc(userId)) {
            categoryNames.put(category.getId(), category.getName());
        }
        YearMonth period = selected;
        List<MonthlyReportResponse.CategoryAmount> byCategory = transactionRepository
                .sumExpenseByCategory(userId, period.atDay(1), period.plusMonths(1).atDay(1))
                .stream()
                .map(row -> new MonthlyReportResponse.CategoryAmount(
                        (Long) row[0],
                        categoryNames.getOrDefault((Long) row[0], "Category"),
                        money(row[1])))
                .sorted(Comparator.comparing(MonthlyReportResponse.CategoryAmount::amount).reversed())
                .toList();

        List<MonthlyReportResponse.BudgetPoint> budgets = budgetService.rowsFor(userId, selected.getYear(), selected.getMonthValue())
                .stream()
                .map(this::budgetPoint)
                .toList();

        List<Loan> loans = loanRepository.findByUserIdOrderByNameAsc(userId);
        List<MonthlyReportResponse.MonthAmount> balances = loanBalances(loans, selected.getYear(), today);
        List<MonthlyReportResponse.InterestSplit> splits = interestSplit(loans, selected.getYear(), today);

        return new MonthlyReportResponse(
                selected.getYear(),
                selected.getMonthValue(),
                cashFlow,
                byCategory,
                spending,
                budgets,
                balances,
                splits);
    }

    private MonthlyReportResponse.BudgetPoint budgetPoint(BudgetResponse budget) {
        return new MonthlyReportResponse.BudgetPoint(
                budget.categoryId(),
                budget.categoryName(),
                budget.amount(),
                budget.spent(),
                budget.remaining(),
                budget.usagePercent(),
                budget.overBudget());
    }

    private List<MonthlyReportResponse.MonthAmount> loanBalances(List<Loan> loans, int year, YearMonth today) {
        List<MonthlyReportResponse.MonthAmount> points = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            YearMonth ym = YearMonth.of(year, month);
            BigDecimal total = Money.ZERO;
            for (Loan loan : loans) {
                total = total.add(balanceAt(loan, ym, today));
            }
            points.add(new MonthlyReportResponse.MonthAmount(year, month, Money.scale(total)));
        }
        return points;
    }

    private BigDecimal balanceAt(Loan loan, YearMonth month, YearMonth today) {
        LocalDate monthEnd = month.atEndOfMonth();
        if (loan.getStartDate().isAfter(monthEnd)) {
            return Money.ZERO;
        }
        if (month.isAfter(today)) {
            return projectedBalance(loan, monthEnd, today);
        }
        return loanPaymentRepository.findByLoanIdOrderByPaymentDateAscIdAsc(loan.getId()).stream()
                .filter(payment -> !payment.getPaymentDate().isAfter(monthEnd))
                .reduce((first, second) -> second)
                .map(payment -> Money.scale(payment.getRemainingPrincipal()))
                .orElse(Money.scale(loan.getPrincipalAmount()));
    }

    private BigDecimal projectedBalance(Loan loan, LocalDate monthEnd, YearMonth today) {
        BigDecimal balance = Money.scale(loan.getOutstandingPrincipal());
        LocalDate due = nextDue(loan, today.atDay(1));
        int guard = 0;
        while (balance.signum() > 0 && !due.isAfter(monthEnd) && guard++ < 600) {
            if (YearMonth.from(due).isAfter(today)) {
                PaymentSplit split = loanCalculationService.split(
                        balance, loan.getAnnualInterestRate(), loan.getEmiAmount(), Money.ZERO);
                balance = split.remaining();
            }
            due = due.plusMonths(1);
        }
        return balance;
    }

    private List<MonthlyReportResponse.InterestSplit> interestSplit(List<Loan> loans, int year, YearMonth today) {
        Map<Integer, BigDecimal> interest = new HashMap<>();
        Map<Integer, BigDecimal> principal = new HashMap<>();
        for (Loan loan : loans) {
            for (LoanPayment payment : loanPaymentRepository.findByLoanIdOrderByPaymentDateAscIdAsc(loan.getId())) {
                if (payment.getPaymentDate().getYear() != year) {
                    continue;
                }
                int month = payment.getPaymentDate().getMonthValue();
                interest.merge(month, payment.getInterestAmount(), BigDecimal::add);
                principal.merge(month, payment.getPrincipalAmount().add(payment.getExtraPrincipalAmount()), BigDecimal::add);
            }
            BigDecimal balance = Money.scale(loan.getOutstandingPrincipal());
            LocalDate due = nextDue(loan, today.atEndOfMonth());
            int guard = 0;
            while (balance.signum() > 0 && due.getYear() <= year && guard++ < 600) {
                YearMonth dueMonth = YearMonth.from(due);
                if (due.getYear() == year && dueMonth.isAfter(today)) {
                    PaymentSplit split = loanCalculationService.split(
                            balance, loan.getAnnualInterestRate(), loan.getEmiAmount(), Money.ZERO);
                    interest.merge(due.getMonthValue(), split.interest(), BigDecimal::add);
                    principal.merge(due.getMonthValue(), split.principal(), BigDecimal::add);
                    balance = split.remaining();
                }
                due = due.plusMonths(1);
            }
        }
        List<MonthlyReportResponse.InterestSplit> points = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            points.add(new MonthlyReportResponse.InterestSplit(
                    year, month, money(interest.get(month)), money(principal.get(month))));
        }
        return points;
    }

    private LocalDate nextDue(Loan loan, LocalDate onOrAfter) {
        LocalDate due = loan.getFirstPaymentDate();
        int guard = 0;
        while (due.isBefore(onOrAfter) && guard++ < 1200) {
            due = due.plusMonths(1);
        }
        return due;
    }

    private BigDecimal money(Object value) {
        if (value == null) {
            return Money.ZERO;
        }
        return Money.scale(new BigDecimal(value.toString()));
    }
}
