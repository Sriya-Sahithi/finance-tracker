package com.financetracker.imports;

import java.time.Instant;

/** A previously parsed upload awaiting user confirmation, scoped to the uploading user. */
record StagedImport(Long userId, ParsedTable table, Instant expiresAt) {
}
