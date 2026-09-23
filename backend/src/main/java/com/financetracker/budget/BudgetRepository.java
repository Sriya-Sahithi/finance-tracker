package com.financetracker.budget;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    Optional<Budget> findByIdAndUserId(Long id, Long userId);

    List<Budget> findByUserIdAndYearAndMonth(Long userId, int year, int month);

    Optional<Budget> findByUserIdAndCategoryIdAndYearAndMonth(Long userId, Long categoryId, int year, int month);

    boolean existsByCategoryId(Long categoryId);
}
