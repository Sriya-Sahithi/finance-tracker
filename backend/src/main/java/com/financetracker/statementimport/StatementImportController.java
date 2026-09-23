package com.financetracker.statementimport;

import com.financetracker.common.exception.StandardApiErrors;
import com.financetracker.statementimport.dto.StatementImportConfirmRequest;
import com.financetracker.statementimport.dto.StatementImportConfirmResponse;
import com.financetracker.statementimport.dto.StatementImportPreviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/statement-imports")
@Tag(name = "Statement Imports")
@StandardApiErrors
public class StatementImportController {

    private final StatementImportService statementImportService;

    public StatementImportController(StatementImportService statementImportService) {
        this.statementImportService = statementImportService;
    }

    @PostMapping("/preview")
    @Operation(summary = "Preview a CSV bank statement import")
    public StatementImportPreviewResponse preview(@RequestPart("file") MultipartFile file) {
        return statementImportService.preview(file);
    }

    @PostMapping("/{sessionId}/confirm")
    @Operation(summary = "Confirm selected rows from a CSV bank statement import preview")
    public ResponseEntity<StatementImportConfirmResponse> confirm(
            @PathVariable UUID sessionId,
            @Valid @org.springframework.web.bind.annotation.RequestBody StatementImportConfirmRequest request
    ) {
        return ResponseEntity.ok(statementImportService.confirm(sessionId, request));
    }
}
