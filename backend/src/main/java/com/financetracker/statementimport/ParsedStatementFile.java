package com.financetracker.statementimport;

import java.util.List;

record ParsedStatementFile(
        List<ParsedStatementRow> rows,
        List<SkippedRowData> skippedRows,
        List<String> warnings,
        String detectedAccountName,
        String detectedAccountNumber,
        int totalRows
) {
    record SkippedRowData(int rowNumber, String reason) {
    }
}
