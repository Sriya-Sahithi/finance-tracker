package com.financetracker.account;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByUserIdOrderByNameAsc(Long userId);

    Optional<Account> findByIdAndUserId(Long id, Long userId);

    Optional<Account> findFirstByUserIdAndAccountNumber(Long userId, String accountNumber);

    List<Account> findByUserIdAndNameIgnoreCase(Long userId, String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id and a.user.id = :userId")
    Optional<Account> lockByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
