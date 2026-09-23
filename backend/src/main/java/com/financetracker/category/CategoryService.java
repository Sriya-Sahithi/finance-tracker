package com.financetracker.category;

import com.financetracker.category.dto.CategoryRequest;
import com.financetracker.category.dto.CategoryResponse;
import com.financetracker.budget.BudgetRepository;
import com.financetracker.common.exception.BadRequestException;
import com.financetracker.common.exception.ConflictException;
import com.financetracker.common.exception.ResourceNotFoundException;
import com.financetracker.common.security.CurrentUserService;
import com.financetracker.transaction.TransactionRepository;
import com.financetracker.user.User;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    public static final List<DefaultCategory> DEFAULTS = List.of(
            new DefaultCategory("Salary", CategoryType.INCOME),
            new DefaultCategory("Freelance", CategoryType.INCOME),
            new DefaultCategory("Investment", CategoryType.INCOME),
            new DefaultCategory("Interest", CategoryType.INCOME),
            new DefaultCategory("Other Income", CategoryType.INCOME),
            new DefaultCategory("Food & Dining", CategoryType.EXPENSE),
            new DefaultCategory("Groceries", CategoryType.EXPENSE),
            new DefaultCategory("Transport", CategoryType.EXPENSE),
            new DefaultCategory("Housing", CategoryType.EXPENSE),
            new DefaultCategory("Utilities", CategoryType.EXPENSE),
            new DefaultCategory("Healthcare", CategoryType.EXPENSE),
            new DefaultCategory("Entertainment", CategoryType.EXPENSE),
            new DefaultCategory("Shopping", CategoryType.EXPENSE),
            new DefaultCategory("Education", CategoryType.EXPENSE),
            new DefaultCategory("Insurance", CategoryType.EXPENSE),
            new DefaultCategory("Personal Care", CategoryType.EXPENSE),
            new DefaultCategory("Loan & EMI", CategoryType.EXPENSE),
            new DefaultCategory("Other Expense", CategoryType.EXPENSE)
    );

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final CurrentUserService currentUserService;

    public CategoryService(
            CategoryRepository categoryRepository,
            TransactionRepository transactionRepository,
            BudgetRepository budgetRepository,
            CurrentUserService currentUserService
    ) {
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public void createDefaults(User user) {
        for (DefaultCategory defaults : DEFAULTS) {
            Category category = new Category();
            category.setUser(user);
            category.setName(defaults.name());
            category.setType(defaults.type());
            categoryRepository.save(category);
        }
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(CategoryType type) {
        Long userId = currentUserService.requireId();
        List<Category> categories = type == null
                ? categoryRepository.findByUserIdOrderByTypeAscNameAsc(userId)
                : categoryRepository.findByUserIdAndTypeOrderByNameAsc(userId, type);
        return categories.stream().map(CategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(Long id) {
        return CategoryResponse.from(require(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        User user = currentUserService.require();
        String name = request.name().trim();
        ensureUnique(user.getId(), request.type(), name, null);
        Category category = new Category();
        category.setUser(user);
        category.setName(name);
        category.setType(request.type());
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = require(id);
        String name = request.name().trim();
        if (category.getType() != request.type() && transactionRepository.existsByCategoryId(category.getId())) {
            throw new BadRequestException("Category type cannot change while transactions use it");
        }
        ensureUnique(category.getUser().getId(), request.type(), name, category.getId());
        category.setName(name);
        category.setType(request.type());
        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(Long id) {
        Category category = require(id);
        if (transactionRepository.existsByCategoryId(category.getId()) || budgetRepository.existsByCategoryId(category.getId())) {
            throw new ConflictException("Category is used by transactions or budgets and cannot be deleted");
        }
        categoryRepository.delete(category);
    }

    public Category requireOwned(Long id, Long userId) {
        return categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    @Transactional(readOnly = true)
    public Category requireImportCategory(Long userId, CategoryType type) {
        return categoryRepository.findByUserIdAndTypeOrderByNameAsc(userId, type).stream()
                .filter(category -> type == CategoryType.INCOME
                        ? "Other Income".equalsIgnoreCase(category.getName())
                        : "Other Expense".equalsIgnoreCase(category.getName()))
                .findFirst()
                .or(() -> categoryRepository.findByUserIdAndTypeOrderByNameAsc(userId, type).stream().findFirst())
                .orElseThrow(() -> new BadRequestException("Create at least one " + type.name().toLowerCase() + " category before importing statements"));
    }

    private Category require(Long id) {
        return requireOwned(id, currentUserService.requireId());
    }

    private void ensureUnique(Long userId, CategoryType type, String name, Long currentId) {
        categoryRepository.findByUserIdAndTypeOrderByNameAsc(userId, type).stream()
                .filter(existing -> existing.getName().equalsIgnoreCase(name))
                .filter(existing -> currentId == null || !existing.getId().equals(currentId))
                .findAny()
                .ifPresent(existing -> {
                    throw new ConflictException("A category with this name already exists");
                });
    }

    public record DefaultCategory(String name, CategoryType type) {
    }
}
