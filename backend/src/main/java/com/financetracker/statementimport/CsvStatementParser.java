package com.financetracker.statementimport;

import com.financetracker.common.exception.BadRequestException;
import com.financetracker.common.money.Money;
import com.financetracker.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class CsvStatementParser {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd/MM/yy"));

    ParsedStatementFile parse(String csvText) {
        if (csvText == null || csvText.isBlank()) {
            throw new BadRequestException("CSV file is empty");
        }
        List<List<String>> rawRows = parseRows(csvText);
        List<List<String>> rows = rawRows.stream()
                .filter(row -> row.stream().anyMatch(value -> value != null && !value.isBlank()))
                .toList();
        if (rows.isEmpty()) {
            throw new BadRequestException("CSV file is empty");
        }

        HeaderMapping header = HeaderMapping.from(rows.get(0));
        List<ParsedStatementRow> parsedRows = new ArrayList<>();
        List<ParsedStatementFile.SkippedRowData> skippedRows = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Integer> accountNames = new LinkedHashMap<>();
        Map<String, Integer> accountNumbers = new LinkedHashMap<>();

        for (int index = 1; index < rows.size(); index++) {
            int rowNumber = index + 1;
            List<String> row = rows.get(index);
            if (row.stream().allMatch(value -> value == null || value.isBlank())) {
                skippedRows.add(new ParsedStatementFile.SkippedRowData(rowNumber, "Blank row"));
                continue;
            }
            if (row.size() != header.size()) {
                throw new BadRequestException("Malformed CSV row " + rowNumber + ": expected " + header.size() + " columns but found " + row.size());
            }
            String accountName = header.value(row, ColumnRole.ACCOUNT_NAME);
            String accountNumber = header.value(row, ColumnRole.ACCOUNT_NUMBER);
            remember(accountNames, accountName);
            remember(accountNumbers, accountNumber);

            String dateValue = header.valueRequired(row, ColumnRole.DATE, rowNumber, "transaction date");
            String description = firstNonBlank(header.value(row, ColumnRole.DESCRIPTION), header.value(row, ColumnRole.REFERENCE));
            if (description == null) {
                skippedRows.add(new ParsedStatementFile.SkippedRowData(rowNumber, "Missing description/narration"));
                continue;
            }
            TransactionAmount amount = resolveAmount(header, row, rowNumber);
            if (amount == null) {
                skippedRows.add(new ParsedStatementFile.SkippedRowData(rowNumber, "No debit, credit, or amount value"));
                continue;
            }
            parsedRows.add(new ParsedStatementRow(
                    rowNumber,
                    parseDate(dateValue, rowNumber),
                    description,
                    blankToNull(header.value(row, ColumnRole.REFERENCE)),
                    amount.type(),
                    amount.amount()));
        }

        String detectedAccountName = detectSingleValue(accountNames, "account name", warnings);
        String detectedAccountNumber = detectSingleValue(accountNumbers, "account number", warnings);
        return new ParsedStatementFile(parsedRows, skippedRows, warnings, detectedAccountName, detectedAccountNumber, rows.size() - 1);
    }

    private TransactionAmount resolveAmount(HeaderMapping header, List<String> row, int rowNumber) {
        BigDecimal debit = parseOptionalAmount(header.value(row, ColumnRole.DEBIT), rowNumber, "debit");
        BigDecimal credit = parseOptionalAmount(header.value(row, ColumnRole.CREDIT), rowNumber, "credit");
        if (debit != null || credit != null) {
            if (debit != null && credit != null && debit.signum() > 0 && credit.signum() > 0) {
                throw new BadRequestException("Malformed CSV row " + rowNumber + ": both debit and credit are populated");
            }
            if (credit != null && credit.signum() > 0) {
                return new TransactionAmount(TransactionType.INCOME, Money.scale(credit.abs()));
            }
            if (debit != null && debit.signum() > 0) {
                return new TransactionAmount(TransactionType.EXPENSE, Money.scale(debit.abs()));
            }
        }

        String amountValue = header.value(row, ColumnRole.AMOUNT);
        if (amountValue == null || amountValue.isBlank()) {
            return null;
        }
        BigDecimal parsedAmount = parseRequiredAmount(amountValue, rowNumber, "amount");
        String typeValue = header.value(row, ColumnRole.TYPE);
        if (typeValue != null && !typeValue.isBlank()) {
            TransactionType type = parseType(typeValue, rowNumber);
            return new TransactionAmount(type, Money.scale(parsedAmount.abs()));
        }
        if (parsedAmount.signum() < 0) {
            return new TransactionAmount(TransactionType.EXPENSE, Money.scale(parsedAmount.abs()));
        }
        if (parsedAmount.signum() > 0) {
            return new TransactionAmount(TransactionType.INCOME, Money.scale(parsedAmount));
        }
        throw new BadRequestException("Malformed CSV row " + rowNumber + ": amount must be greater than zero");
    }

    private BigDecimal parseOptionalAmount(String value, int rowNumber, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseRequiredAmount(value, rowNumber, field);
    }

    private BigDecimal parseRequiredAmount(String value, int rowNumber, String field) {
        String normalized = value.trim().toUpperCase(Locale.ROOT)
                .replace(",", "")
                .replace("₹", "")
                .replace("INR", "")
                .replace("RS.", "")
                .replace("RS", "")
                .trim();
        boolean negative = false;
        if (normalized.startsWith("(") && normalized.endsWith(")")) {
            negative = true;
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        if (normalized.endsWith("DR")) {
            negative = true;
            normalized = normalized.substring(0, normalized.length() - 2).trim();
        } else if (normalized.endsWith("CR")) {
            normalized = normalized.substring(0, normalized.length() - 2).trim();
        }
        if (normalized.startsWith("-")) {
            negative = true;
            normalized = normalized.substring(1).trim();
        } else if (normalized.startsWith("+")) {
            normalized = normalized.substring(1).trim();
        }
        try {
            BigDecimal amount = new BigDecimal(normalized);
            return negative ? amount.negate() : amount;
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Malformed CSV row " + rowNumber + ": invalid " + field + " value");
        }
    }

    private LocalDate parseDate(String value, int rowNumber) {
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(value.trim(), formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        throw new BadRequestException("Malformed CSV row " + rowNumber + ": unsupported transaction date '" + value + "'");
    }

    private TransactionType parseType(String value, int rowNumber) {
        String normalized = normalizeKey(value);
        return switch (normalized) {
            case "credit", "cr", "income", "deposit" -> TransactionType.INCOME;
            case "debit", "dr", "expense", "withdrawal", "payment" -> TransactionType.EXPENSE;
            default -> throw new BadRequestException("Malformed CSV row " + rowNumber + ": unsupported transaction type '" + value + "'");
        };
    }

    private List<List<String>> parseRows(String text) {
        String normalized = text.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n');
        List<List<String>> rows = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int index = 0; index < normalized.length(); index++) {
            char ch = normalized.charAt(index);
            if (ch == '"') {
                if (inQuotes && index + 1 < normalized.length() && normalized.charAt(index + 1) == '"') {
                    field.append('"');
                    index++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }
            if (ch == ',' && !inQuotes) {
                currentRow.add(field.toString().trim());
                field.setLength(0);
                continue;
            }
            if (ch == '\n' && !inQuotes) {
                currentRow.add(field.toString().trim());
                rows.add(currentRow);
                currentRow = new ArrayList<>();
                field.setLength(0);
                continue;
            }
            field.append(ch);
        }
        if (inQuotes) {
            throw new BadRequestException("Malformed CSV: unclosed quoted field");
        }
        currentRow.add(field.toString().trim());
        rows.add(currentRow);
        return rows;
    }

    private String detectSingleValue(Map<String, Integer> counts, String label, List<String> warnings) {
        if (counts.isEmpty()) {
            return null;
        }
        String selected = null;
        int max = -1;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > max) {
                selected = entry.getKey();
                max = entry.getValue();
            }
        }
        if (counts.size() > 1) {
            warnings.add("Multiple " + label + " values were detected in the CSV. Using '" + selected + "' for suggestions.");
        }
        return selected;
    }

    private void remember(Map<String, Integer> counts, String value) {
        String normalized = blankToNull(value);
        if (normalized != null) {
            counts.merge(normalized, 1, Integer::sum);
        }
    }

    private String firstNonBlank(String first, String second) {
        String firstValue = blankToNull(first);
        return firstValue != null ? firstValue : blankToNull(second);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeKey(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private record TransactionAmount(TransactionType type, BigDecimal amount) {
    }

    enum ColumnRole {
        DATE,
        DESCRIPTION,
        DEBIT,
        CREDIT,
        AMOUNT,
        TYPE,
        REFERENCE,
        ACCOUNT_NAME,
        ACCOUNT_NUMBER
    }

    private static final class HeaderMapping {
        private static final Map<ColumnRole, List<String>> ALIASES = aliasMap();

        private final Map<ColumnRole, Integer> indexes;
        private final int size;

        private HeaderMapping(Map<ColumnRole, Integer> indexes, int size) {
            this.indexes = indexes;
            this.size = size;
        }

        static HeaderMapping from(List<String> headerRow) {
            Map<ColumnRole, Integer> indexes = new EnumMap<>(ColumnRole.class);
            for (int index = 0; index < headerRow.size(); index++) {
                String normalized = normalizeHeader(headerRow.get(index));
                for (Map.Entry<ColumnRole, List<String>> entry : ALIASES.entrySet()) {
                    if (entry.getValue().contains(normalized) && !indexes.containsKey(entry.getKey())) {
                        indexes.put(entry.getKey(), index);
                    }
                }
            }
            if (!indexes.containsKey(ColumnRole.DATE)) {
                throw new BadRequestException("CSV header must include a transaction date column");
            }
            if (!indexes.containsKey(ColumnRole.DESCRIPTION) && !indexes.containsKey(ColumnRole.REFERENCE)) {
                throw new BadRequestException("CSV header must include a description/narration or reference column");
            }
            if (!(indexes.containsKey(ColumnRole.DEBIT) || indexes.containsKey(ColumnRole.CREDIT) || indexes.containsKey(ColumnRole.AMOUNT))) {
                throw new BadRequestException("CSV header must include debit, credit, or amount columns");
            }
            if (indexes.containsKey(ColumnRole.AMOUNT) && !indexes.containsKey(ColumnRole.TYPE)
                    && !indexes.containsKey(ColumnRole.DEBIT) && !indexes.containsKey(ColumnRole.CREDIT)) {
                return new HeaderMapping(indexes, headerRow.size());
            }
            return new HeaderMapping(indexes, headerRow.size());
        }

        int size() {
            return size;
        }

        String value(List<String> row, ColumnRole role) {
            Integer index = indexes.get(role);
            if (index == null || index >= row.size()) {
                return null;
            }
            return row.get(index);
        }

        String valueRequired(List<String> row, ColumnRole role, int rowNumber, String label) {
            String value = value(row, role);
            if (value == null || value.isBlank()) {
                throw new BadRequestException("Malformed CSV row " + rowNumber + ": missing " + label);
            }
            return value;
        }

        private static String normalizeHeader(String value) {
            return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        }

        private static Map<ColumnRole, List<String>> aliasMap() {
            Map<ColumnRole, List<String>> aliases = new EnumMap<>(ColumnRole.class);
            aliases.put(ColumnRole.DATE, List.of("date", "transactiondate", "txndate", "valuedate", "postingdate"));
            aliases.put(ColumnRole.DESCRIPTION, List.of("description", "narration", "particulars", "details", "remarks"));
            aliases.put(ColumnRole.DEBIT, List.of("debit", "withdrawal", "withdrawn", "debitamount", "withdrawalamount"));
            aliases.put(ColumnRole.CREDIT, List.of("credit", "deposit", "creditamount", "depositamount"));
            aliases.put(ColumnRole.AMOUNT, List.of("amount", "transactionamount", "txnamount"));
            aliases.put(ColumnRole.TYPE, List.of("type", "transactiontype", "drcr"));
            aliases.put(ColumnRole.REFERENCE, List.of("reference", "ref", "refno", "chequeno", "utr", "transactionid"));
            aliases.put(ColumnRole.ACCOUNT_NAME, List.of("accountname", "bankaccountname", "account"));
            aliases.put(ColumnRole.ACCOUNT_NUMBER, List.of("accountnumber", "accountno", "acctnumber", "acctno"));
            return aliases;
        }
    }
}
