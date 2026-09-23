package com.financetracker.common.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(name = "ApiError", description = "Consistent error body. Stack traces are never included.")
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldViolation> fieldErrors
) {
    @Schema(name = "FieldViolation")
    public record FieldViolation(String field, String message) {
    }
}
