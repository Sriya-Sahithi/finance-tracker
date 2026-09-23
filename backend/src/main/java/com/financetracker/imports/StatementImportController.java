package com.financetracker.imports;

import com.financetracker.common.exception.StandardApiErrors;
import com.financetracker.imports.dto.StatementCommitRequest;
import com.financetracker.imports.dto.StatementCommitResponse;
import com.financetracker.imports.dto.StatementParsePreviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports/statements")
@Tag(name = "Statement imports")
@StandardApiErrors
public class StatementImportController {

    private final StatementImportService statementImportService;

    public StatementImportController(StatementImportService statementImportService) {
        this.statementImportService = statementImportService;
    }

    @PostMapping(value = "/preview", consumes = "multipart/form-data")
    @Operation(summary = "Parse a bank/loan statement file and return a preview for column mapping")
    public StatementParsePreviewResponse preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam("targetType") ImportTargetType targetType
    ) {
        return statementImportService.preview(file, targetType);
    }

    @PostMapping("/commit")
    @Operation(summary = "Confirm the column mapping and import transactions or update balances")
    public StatementCommitResponse commit(@Valid @RequestBody StatementCommitRequest request) {
        return statementImportService.commit(request);
    }
}
