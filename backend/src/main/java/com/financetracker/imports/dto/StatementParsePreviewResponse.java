package com.financetracker.imports.dto;

import com.financetracker.imports.ImportTargetType;
import java.util.List;
import java.util.Map;

public record StatementParsePreviewResponse(
        String importToken,
        ImportTargetType targetType,
        List<String> headers,
        List<Map<String, String>> sampleRows,
        StatementColumnMapping suggestedMapping,
        int rowCount
) {
}
