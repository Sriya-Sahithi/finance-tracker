package com.financetracker.dashboard;

import com.financetracker.account.Account;
import com.financetracker.account.AccountRepository;
import com.financetracker.budget.BudgetService;
import com.financetracker.budget.dto.BudgetResponse;
import com.financetracker.common.money.Money;
import com.financetracker.common.security.CurrentUserService;
import com.financetracker.dashboard.dto.DashboardResponse;
import com.financetracker.loan.Loan;
import com.financetracker.loan.LoanCalculationService;
import com.financetracker.loan.LoanRepository;
import com.financetracker.loan.PaymentSplit;
import com.financetracker.transaction.TransactionRepository;
import com.financetracker.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final TransactionRepository transactionRepository;
    private final BudgetService budgetService;
    private final LoanRepository loanRepository;
    private final AccountRepository accountRepository;
    private final LoanCalculationService loanCalculationService;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public DashboardService(
            TransactionRepository transactionRepository,
            BudgetService budgetService,
            LoanRepository loanRepository,
            AccountRepository accountRepository,
            LoanCalculationService loanCalculationService,
            CurrentUserService currentUserService,
            Clock clock
    ) {
        this.transactionRepository = transactionRepository;
        this.budgetService = budgetService;
        this.loanRepository = loanRepository;
        this.accountRepository = accountRepository;
        this.loanCalculationService = loanCalculationService;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(Integer year, Integer month) {
        YearMonth selected = resolve(year, month);
        Long userId = currentUserService.requireId();
        BigDecimal income = money(transactionRepository.sumByType(
                userId, TransactionType.INCOME, selected.atDay(1), selected.plusMonths(1).atDay(1)));
        BigDecimal expenses = money(transactionRepository.sumByType(
                userId, TransactionType.EXPENSE, selected.atDay(1), selected.plusMonths(1).atDay(1)));
        List<BudgetResponse> budgets = budgetService.rowsFor(userId, selected.getYear(), selected.getMonthValue());
        BigDecimal totalBudget = Money.ZERO;
        BigDecimal budgetUsed = Money.ZERO;
        for (BudgetResponse budget : budgets) {
            totalBudget = totalBudget.add(budget.amount());
            budgetUsed = budgetUsed.add(budget.spent());
        }
        totalBudget = Money.scale(totalBudget);
        budgetUsed = Money.scale(budgetUsed);

        List<Account> accounts = accountRepository.findByUserIdOrderByNameAsc(userId);
        List<DashboardResponse.AccountBalanceSummary> accountBalances = accounts.stream()
                .map(account -> new DashboardResponse.AccountBalanceSummary(
                        account.getId(),
                        account.getName(),
                        account.getType().name(),
                        Money.scale(account.getCurrentBalance())))
                .toList();
        BigDecimal totalAccountBalance = accountBalances.stream()
                .map(DashboardResponse.AccountBalanceSummary::balance)
                .reduce(Money.ZERO, BigDecimal::add);

        return new DashboardResponse(
                selected.getYear(),
                selected.getMonthValue(),
                income,
                expenses,
                Money.scale(income.subtract(expenses)),
                totalBudget,
                budgetUsed,
                Money.scale(totalBudget.subtract(budgetUsed)),
                Money.scale(totalAccountBalance),
                accountBalances,
                loanSummary(userId, selected));
    }

    private DashboardResponse.LoanSummary loanSummary(Long userId, YearMonth selected) {
        BigDecimal outstanding = Money.ZERO;
        BigDecimal obligation = Money.ZERO;
        BigDecimal upcoming = Money.ZERO;
        int active = 0;
        List<DashboardResponse.UpcomingPayment> payments = new ArrayList<>();
        for (Loan loan : loanRepository.findByUserIdOrderByNameAsc(userId)) {
            if (loan.getOutstandingPrincipal().signum() <= 0) {
                continue;
            }
            active++;
            outstanding = outstanding.add(loan.getOutstandingPrincipal());
            obligation = obligation.add(loan.getEmiAmount());
            LocalDate due = LocalDate.of(selected.getYear(), selected.getMonthValue(), loan.getPaymentDueDay());
            if (!due.isBefore(loan.getFirstPaymentDate())) {
                PaymentSplit split = loanCalculationService.split(
                        loan.getOutstandingPrincipal(),
                        loan.getAnnualInterestRate(),
                        loan.getEmiAmount(),
                        Money.ZERO);
                upcoming = upcoming.add(split.total());
                payments.add(new DashboardResponse.UpcomingPayment(
                        loan.getId(),
                        loan.getName(),
                        due,
                        split.total(),
                        Money.scale(loan.getOutstandingPrincipal())));
            }
        }
        return new DashboardResponse.LoanSummary(
                Money.scale(outstanding),
                Money.scale(obligation),
                Money.scale(upcoming),
                active,
                payments);
    }

    private YearMonth resolve(Integer year, Integer month) {
        YearMonth now = YearMonth.now(clock);
        if (year == null && month == null) {
            return now;
        }
        if (year == null || month == null) {
            throw new com.financetracker.common.exception.BadRequestException("Provide both year and month");
        }
        return YearMonth.of(year, month);
    }

    private BigDecimal money(BigDecimal value) {
        return Money.scale(value == null ? BigDecimal.ZERO : value);
    }
}
