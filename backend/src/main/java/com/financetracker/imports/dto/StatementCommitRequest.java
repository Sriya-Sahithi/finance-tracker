package com.financetracker.imports.dto;

import com.financetracker.imports.ImportTargetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StatementCommitRequest(
        @NotBlank String importToken,
        @NotNull ImportTargetType targetType,
        @NotNull Long targetId,
        @NotNull @Valid StatementColumnMapping mapping
) {
}
