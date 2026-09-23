package com.financetracker.creditreport;

import com.financetracker.common.exception.BadRequestException;
import com.financetracker.creditreport.dto.CreditReportAccountDto;
import com.financetracker.creditreport.dto.CreditReportParsePreviewResponse;
import com.financetracker.imports.FileParsingService;
import com.financetracker.imports.ParsedTable;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Best-effort extraction of tradeline accounts from a CIBIL-style credit report.
 * PDF reports vary widely in layout, so a line/keyword heuristic is used and the
 * result is always shown to the user for confirmation before anything is saved.
 */
@Service
public class CreditReportParsingService {

    private static final Pattern ACCOUNT_BLOCK_SPLIT =
            Pattern.compile("(?=(?:Bank|Lender|Institution)\\s*[:\\-])", Pattern.CASE_INSENSITIVE);
    private static final Pattern BANK_PATTERN =
            Pattern.compile("(?:Bank|Lender|Institution)\\s*[:\\-]\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TYPE_PATTERN =
            Pattern.compile("(?:Account\\s*Type|Type)\\s*[:\\-]\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BALANCE_PATTERN = Pattern.compile(
            "(?:Current\\s*Balance|Outstanding(?:\\s*Balance)?|Balance)\\s*[:\\-]\\s*([0-9,.]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LIMIT_PATTERN = Pattern.compile(
            "(?:Credit\\s*Limit|Sanctioned\\s*Amount|Limit)\\s*[:\\-]\\s*([0-9,.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern STATUS_PATTERN = Pattern.compile(
            "Status\\s*[:\\-]\\s*(Active|Closed|Open|Settled|Written[- ]?Off)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile(
            "Account\\s*(?:No\\.?|Number)\\s*[:\\-]\\s*([\\w*Xx-]+)", Pattern.CASE_INSENSITIVE);

    private final FileParsingService fileParsingService;

    public CreditReportParsingService(FileParsingService fileParsingService) {
        this.fileParsingService = fileParsingService;
    }

    public CreditReportParsePreviewResponse parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("A file is required");
        }
        String filename = fileParsingService.sanitizeFilename(file.getOriginalFilename());
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return parsePdf(file, filename);
        }
        if (lower.endsWith(".xlsx")) {
            return parseSpreadsheet(file, filename);
        }
        throw new BadRequestException("Unsupported file type. Upload a .pdf or .xlsx credit report");
    }

    private CreditReportParsePreviewResponse parsePdf(MultipartFile file, String filename) {
        String text;
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            text = new PDFTextStripper().getText(document);
        } catch (IOException ex) {
            throw new BadRequestException("Could not read PDF report");
        }

        List<CreditReportAccountDto> accounts = new ArrayList<>();
        for (String block : ACCOUNT_BLOCK_SPLIT.split(text)) {
            if (block.isBlank()) {
                continue;
            }
            Matcher bankMatcher = BANK_PATTERN.matcher(block);
            if (!bankMatcher.find()) {
                continue;
            }
            Matcher balanceMatcher = BALANCE_PATTERN.matcher(block);
            BigDecimal balance = balanceMatcher.find() ? parseNumber(balanceMatcher.group(1)) : null;
            if (balance == null) {
                continue;
            }
            String bankName = bankMatcher.group(1).trim();
            Matcher limitMatcher = LIMIT_PATTERN.matcher(block);
            BigDecimal limit = limitMatcher.find() ? parseNumber(limitMatcher.group(1)) : null;
            Matcher typeMatcher = TYPE_PATTERN.matcher(block);
            CreditReportAccountType type = typeMatcher.find()
                    ? mapAccountType(typeMatcher.group(1))
                    : CreditReportAccountType.OTHER;
            Matcher statusMatcher = STATUS_PATTERN.matcher(block);
            CreditReportStatus status = statusMatcher.find()
                    ? mapStatus(statusMatcher.group(1))
                    : CreditReportStatus.ACTIVE;
            Matcher accountNumberMatcher = ACCOUNT_NUMBER_PATTERN.matcher(block);
            String accountNumber = accountNumberMatcher.find() ? accountNumberMatcher.group(1).trim() : null;
            accounts.add(new CreditReportAccountDto(null, bankName, type, accountNumber, balance, limit, status, null));
        }

        String warning = accounts.isEmpty()
                ? "Could not automatically detect account blocks in this PDF layout. Please add accounts manually below."
                : null;
        return new CreditReportParsePreviewResponse(accounts, filename, warning);
    }

    private CreditReportParsePreviewResponse parseSpreadsheet(MultipartFile file, String filename) {
        ParsedTable table = fileParsingService.parse(file);
        String bankCol = find(table.headers(), "bank", "lender", "institution");
        String typeCol = find(table.headers(), "type");
        String balanceCol = find(table.headers(), "balance", "outstanding");
        String limitCol = find(table.headers(), "limit", "sanctioned");
        String statusCol = find(table.headers(), "status");
        String accountNumberCol = find(table.headers(), "account no", "account number", "acc no");
        if (bankCol == null || balanceCol == null) {
            throw new BadRequestException("Could not find bank name / balance columns in the spreadsheet");
        }

        List<CreditReportAccountDto> accounts = new ArrayList<>();
        for (Map<String, String> row : table.rows()) {
            String bankName = row.get(bankCol);
            BigDecimal balance = parseNumber(row.get(balanceCol));
            if (bankName == null || bankName.isBlank() || balance == null) {
                continue;
            }
            BigDecimal limit = limitCol != null ? parseNumber(row.get(limitCol)) : null;
            CreditReportAccountType type = typeCol != null ? mapAccountType(row.get(typeCol)) : CreditReportAccountType.OTHER;
            CreditReportStatus status = statusCol != null ? mapStatus(row.get(statusCol)) : CreditReportStatus.ACTIVE;
            String accountNumber = accountNumberCol != null ? row.get(accountNumberCol) : null;
            accounts.add(new CreditReportAccountDto(null, bankName.trim(), type, accountNumber, balance, limit, status, null));
        }
        if (accounts.isEmpty()) {
            throw new BadRequestException("No account rows could be parsed from the spreadsheet");
        }
        return new CreditReportParsePreviewResponse(accounts, filename, null);
    }

    private String find(List<String> headers, String... keywords) {
        for (String header : headers) {
            String lower = header.toLowerCase(Locale.ROOT);
            for (String keyword : keywords) {
                if (lower.contains(keyword)) {
                    return header;
                }
            }
        }
        return null;
    }

    private CreditReportAccountType mapAccountType(String raw) {
        if (raw == null) {
            return CreditReportAccountType.OTHER;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("credit card")) {
            return CreditReportAccountType.CREDIT_CARD;
        }
        if (lower.contains("loan")) {
            return CreditReportAccountType.LOAN;
        }
        if (lower.contains("bank") || lower.contains("saving") || lower.contains("current")) {
            return CreditReportAccountType.BANK;
        }
        return CreditReportAccountType.OTHER;
    }

    private CreditReportStatus mapStatus(String raw) {
        if (raw == null) {
            return CreditReportStatus.ACTIVE;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("closed") || lower.contains("settled") || lower.contains("written")) {
            return CreditReportStatus.CLOSED;
        }
        return CreditReportStatus.ACTIVE;
    }

    private BigDecimal parseNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.replaceAll("[^0-9.\\-]", "");
        if (cleaned.isBlank() || cleaned.equals("-")) {
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
