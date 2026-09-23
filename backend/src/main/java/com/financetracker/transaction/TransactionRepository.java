package com.financetracker.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    boolean existsByCategoryId(Long categoryId);

    @Query("select count(t) > 0 from Transaction t where t.account.id = :accountId or t.transferAccount.id = :accountId")
    boolean existsForAccount(@Param("accountId") Long accountId);

    @Query("""
            select t from Transaction t
            left join fetch t.category
            left join fetch t.account
            left join fetch t.transferAccount
            where t.user.id = :userId
              and (t.account.id = :accountId or t.transferAccount.id = :accountId)
            order by t.transactionDate desc, t.id desc
            """)
    List<Transaction> findRecentForAccount(
            @Param("userId") Long userId,
            @Param("accountId") Long accountId,
            Pageable pageable
    );

    @Query("""
            select coalesce(sum(t.amount), 0)
            from Transaction t
            where t.user.id = :userId
              and t.type = :type
              and t.transactionDate >= :start
              and t.transactionDate < :end
            """)
    BigDecimal sumByType(
            @Param("userId") Long userId,
            @Param("type") TransactionType type,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    @Query("""
            select t.category.id, coalesce(sum(t.amount), 0)
            from Transaction t
            where t.user.id = :userId
              and t.type = com.financetracker.transaction.TransactionType.EXPENSE
              and t.transactionDate >= :start
              and t.transactionDate < :end
              and t.category is not null
            group by t.category.id
            """)
    List<Object[]> sumExpenseByCategory(
            @Param("userId") Long userId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    @Query("""
            select month(t.transactionDate), t.type, coalesce(sum(t.amount), 0)
            from Transaction t
            where t.user.id = :userId
              and t.transactionDate >= :start
              and t.transactionDate < :end
              and t.type in (
                com.financetracker.transaction.TransactionType.INCOME,
                com.financetracker.transaction.TransactionType.EXPENSE
              )
            group by month(t.transactionDate), t.type
            """)
    List<Object[]> sumMonthlyCashflow(
            @Param("userId") Long userId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    @Query("select t.importRowFingerprint from Transaction t where t.user.id = :userId and t.account.id = :accountId and t.importRowFingerprint in :fingerprints")
    List<String> findExistingImportRowFingerprints(@Param("userId") Long userId, @Param("accountId") Long accountId, @Param("fingerprints") List<String> fingerprints);
}
