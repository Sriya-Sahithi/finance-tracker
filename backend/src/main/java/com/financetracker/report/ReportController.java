package com.financetracker.report;

import com.financetracker.common.exception.StandardApiErrors;
import com.financetracker.report.dto.MonthlyReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports")
@StandardApiErrors
@Validated
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/monthly")
    @Operation(summary = "Monthly cash flow, category spending, budget use, and loan series")
    public MonthlyReportResponse monthly(
            @RequestParam(required = false) @Min(2000) @Max(2100) Integer year,
            @RequestParam(required = false) @Min(1) @Max(12) Integer month
    ) {
        return reportService.monthly(year, month);
    }
}
