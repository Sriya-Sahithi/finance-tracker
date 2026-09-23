package com.financetracker.creditreport;

import com.financetracker.common.exception.StandardApiErrors;
import com.financetracker.creditreport.dto.CreditReportAccountDto;
import com.financetracker.creditreport.dto.CreditReportCommitRequest;
import com.financetracker.creditreport.dto.CreditReportParsePreviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/credit-reports")
@Tag(name = "Credit reports")
@StandardApiErrors
public class CreditReportController {

    private final CreditReportParsingService parsingService;
    private final CreditReportService creditReportService;

    public CreditReportController(CreditReportParsingService parsingService, CreditReportService creditReportService) {
        this.parsingService = parsingService;
        this.creditReportService = creditReportService;
    }

    @GetMapping
    @Operation(summary = "List saved credit report accounts for the current user")
    public List<CreditReportAccountDto> list() {
        return creditReportService.list();
    }

    @PostMapping(value = "/preview", consumes = "multipart/form-data")
    @Operation(summary = "Parse a CIBIL/credit report PDF or spreadsheet for review")
    public CreditReportParsePreviewResponse preview(@RequestParam("file") MultipartFile file) {
        return parsingService.parse(file);
    }

    @PostMapping("/commit")
    @Operation(summary = "Save the confirmed credit report accounts")
    public List<CreditReportAccountDto> commit(@Valid @RequestBody CreditReportCommitRequest request) {
        return creditReportService.commit(request);
    }
}
