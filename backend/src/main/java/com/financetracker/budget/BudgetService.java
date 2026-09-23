package com.financetracker.budget;

import com.financetracker.budget.dto.BudgetRequest;
import com.financetracker.budget.dto.BudgetResponse;
import com.financetracker.category.Category;
import com.financetracker.category.CategoryService;
import com.financetracker.category.CategoryType;
import com.financetracker.common.exception.BadRequestException;
import com.financetracker.common.exception.ConflictException;
import com.financetracker.common.exception.ResourceNotFoundException;
import com.financetracker.common.money.Money;
import com.financetracker.common.security.CurrentUserService;
import com.financetracker.transaction.TransactionRepository;
import com.financetracker.user.User;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryService categoryService;
    private final TransactionRepository transactionRepository;
    private final CurrentUserService currentUserService;

    public BudgetService(
            BudgetRepository budgetRepository,
            CategoryService categoryService,
            TransactionRepository transactionRepository,
            CurrentUserService currentUserService
    ) {
        this.budgetRepository = budgetRepository;
        this.categoryService = categoryService;
        this.transactionRepository = transactionRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> list(int year, int month) {
        User user = currentUserService.require();
        return rowsFor(user.getId(), year, month);
    }

    @Transactional(readOnly = true)
    public BudgetResponse get(Long id) {
        Budget budget = require(id);
        BigDecimal spent = spentFor(budget.getUser().getId(), YearMonth.of(budget.getYear(), budget.getMonth()))
                .getOrDefault(budget.getCategory().getId(), Money.ZERO);
        return toResponse(budget, spent);
    }

    @Transactional
    public BudgetResponse create(BudgetRequest request) {
        User user = currentUserService.require();
        Category category = expenseCategory(user.getId(), request.categoryId());
        if (budgetRepository.findByUserIdAndCategoryIdAndYearAndMonth(
                user.getId(), category.getId(), request.year(), request.month()).isPresent()) {
            throw new ConflictException("A budget already exists for this category and month");
        }
        Budget budget = new Budget();
        budget.setUser(user);
        budget.setCategory(category);
        budget.setYear(request.year());
        budget.setMonth(request.month());
        budget.setAmount(Money.scale(request.amount()));
        budgetRepository.save(budget);
        BigDecimal spent = spentFor(user.getId(), YearMonth.of(request.year(), request.month()))
                .getOrDefault(category.getId(), Money.ZERO);
        return toResponse(budget, spent);
    }

    @Transactional
    public BudgetResponse update(Long id, BudgetRequest request) {
        Budget budget = require(id);
        Category category = expenseCategory(budget.getUser().getId(), request.categoryId());
        budgetRepository.findByUserIdAndCategoryIdAndYearAndMonth(
                        budget.getUser().getId(), category.getId(), request.year(), request.month())
                .filter(existing -> !existing.getId().equals(budget.getId()))
                .ifPresent(existing -> {
                    throw new ConflictException("A budget already exists for this category and month");
                });
        budget.setCategory(category);
        budget.setYear(request.year());
        budget.setMonth(request.month());
        budget.setAmount(Money.scale(request.amount()));
        BigDecimal spent = spentFor(budget.getUser().getId(), YearMonth.of(request.year(), request.month()))
                .getOrDefault(category.getId(), Money.ZERO);
        return toResponse(budget, spent);
    }

    @Transactional
    public void delete(Long id) {
        budgetRepository.delete(require(id));
    }

    public List<BudgetResponse> rowsFor(Long userId, int year, int month) {
        Map<Long, BigDecimal> spent = spentFor(userId, YearMonth.of(year, month));
        return budgetRepository.findByUserIdAndYearAndMonth(userId, year, month).stream()
                .map(budget -> toResponse(budget, spent.getOrDefault(budget.getCategory().getId(), Money.ZERO)))
                .toList();
    }

    public Map<Long, BigDecimal> spentFor(Long userId, YearMonth month) {
        Map<Long, BigDecimal> spent = new HashMap<>();
        for (Object[] row : transactionRepository.sumExpenseByCategory(userId, month.atDay(1), month.plusMonths(1).atDay(1))) {
            spent.put((Long) row[0], Money.scale(new BigDecimal(row[1].toString())));
        }
        return spent;
    }

    private BudgetResponse toResponse(Budget budget, BigDecimal spent) {
        BudgetMath.Usage usage = BudgetMath.evaluate(budget.getAmount(), spent);
        return new BudgetResponse(
                budget.getId(),
                budget.getCategory().getId(),
                budget.getCategory().getName(),
                budget.getYear(),
                budget.getMonth(),
                usage.budget(),
                usage.spent(),
                usage.remaining(),
                usage.usagePercent(),
                usage.overBudget(),
                budget.getCreatedAt(),
                budget.getUpdatedAt());
    }

    private Category expenseCategory(Long userId, Long categoryId) {
        Category category = categoryService.requireOwned(categoryId, userId);
        if (category.getType() != CategoryType.EXPENSE) {
            throw new BadRequestException("Budgets can only be set on expense categories");
        }
        return category;
    }

    private Budget require(Long id) {
        return budgetRepository.findByIdAndUserId(id, currentUserService.requireId())
                .orElseThrow(() -> new ResourceNotFoundException("Budget not found"));
    }
}
