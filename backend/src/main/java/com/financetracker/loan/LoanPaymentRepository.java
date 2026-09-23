package com.financetracker.loan;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanPaymentRepository extends JpaRepository<LoanPayment, Long> {

    List<LoanPayment> findByLoanIdOrderByPaymentDateAscIdAsc(Long loanId);

    Optional<LoanPayment> findByIdAndLoanId(Long id, Long loanId);

    boolean existsByLoanId(Long loanId);
}
