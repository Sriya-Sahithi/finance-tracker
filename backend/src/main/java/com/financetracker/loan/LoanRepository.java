package com.financetracker.loan;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    List<Loan> findByUserIdOrderByNameAsc(Long userId);

    Optional<Loan> findByIdAndUserId(Long id, Long userId);
}
