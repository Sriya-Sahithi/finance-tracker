package com.financetracker.creditreport;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditReportAccountRepository extends JpaRepository<CreditReportAccount, Long> {

    List<CreditReportAccount> findByUserIdOrderByBankNameAsc(Long userId);
}
