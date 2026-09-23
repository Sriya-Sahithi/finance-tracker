package com.financetracker.statementimport;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatementImportSessionRepository extends JpaRepository<StatementImportSession, UUID> {

    Optional<StatementImportSession> findByIdAndUserId(UUID id, Long userId);
}
