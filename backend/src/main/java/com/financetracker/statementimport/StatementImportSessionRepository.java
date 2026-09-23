package com.financetracker.statementimport;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StatementImportSessionRepository extends JpaRepository<StatementImportSession, UUID> {

    Optional<StatementImportSession> findByIdAndUserId(UUID id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StatementImportSession s where s.id = :id and s.user.id = :userId")
    Optional<StatementImportSession> lockByIdAndUserId(@Param("id") UUID id, @Param("userId") Long userId);
}
