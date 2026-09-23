package com.financetracker.dashboard;

import com.financetracker.budget.BudgetService;
import com.financetracker.budget.dto.BudgetResponse;
import com.financetracker.common.money.Money;
import com.financetracker.common.security.CurrentUserService;
import com.financetracker.dashboard.dto.DashboardResponse;
import com.financetracker.transaction.TransactionRepository;
import com.financetracker.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final TransactionRepository transactionRepository;
    private final BudgetService budgetService;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public DashboardService(
            TransactionRepository transactionRepository,
            BudgetService budgetService,
            CurrentUserService currentUserService,
            Clock clock
    ) {
        this.transactionRepository = transactionRepository;
        this.budgetService = budgetService;
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
        return new DashboardResponse(
                selected.getYear(),
                selected.getMonthValue(),
                income,
                expenses,
                Money.scale(income.subtract(expenses)),
                totalBudget,
                budgetUsed,
                Money.scale(totalBudget.subtract(budgetUsed)),
                emptyLoans());
    }

    private DashboardResponse.LoanSummary emptyLoans() {
        return new DashboardResponse.LoanSummary(Money.ZERO, Money.ZERO, Money.ZERO, 0, List.of());
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
