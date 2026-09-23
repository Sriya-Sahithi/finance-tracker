package com.financetracker.imports;

import com.financetracker.common.exception.BadRequestException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Parses uploaded bank/loan statement files into a generic tabular structure. */
@Service
public class FileParsingService {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_ROWS = 5000;

    public ParsedTable parse(MultipartFile file) {
        ImportedFileType type = detectType(file);
        return switch (type) {
            case CSV -> parseCsv(file);
            case XLSX -> parseXlsx(file);
        };
    }

    public ImportedFileType detectType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("A file is required");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("File exceeds the 10MB upload limit");
        }
        String filename = sanitizeFilename(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (filename.endsWith(".csv")) {
            return ImportedFileType.CSV;
        }
        if (filename.endsWith(".xlsx") && isZipMagic(peek(file, 4))) {
            return ImportedFileType.XLSX;
        }
        throw new BadRequestException("Unsupported file type. Upload a .csv or .xlsx file");
    }

    /** Strips directory components and disallowed characters so the name is safe to log/display. */
    public String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) {
            return "upload";
        }
        String name = original.replace("\\", "/");
        name = name.substring(name.lastIndexOf('/') + 1);
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private byte[] peek(MultipartFile file, int length) {
        try (var in = file.getInputStream()) {
            byte[] buffer = new byte[length];
            int read = in.read(buffer);
            return read <= 0 ? new byte[0] : buffer;
        } catch (IOException ex) {
            throw new BadRequestException("Could not read uploaded file");
        }
    }

    private boolean isZipMagic(byte[] header) {
        // .xlsx files are zip archives, which start with the "PK" magic bytes.
        return header.length >= 2 && header[0] == 0x50 && header[1] == 0x4B;
    }

    private ParsedTable parseCsv(MultipartFile file) {
        try (var reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreSurroundingSpaces(true)
                    .setTrim(true)
                    .build();
            CSVParser parser = format.parse(reader);
            List<String> headers = new ArrayList<>(parser.getHeaderNames());
            if (headers.isEmpty()) {
                throw new BadRequestException("The CSV file has no header row");
            }
            List<Map<String, String>> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                if (rows.size() >= MAX_ROWS) {
                    break;
                }
                Map<String, String> row = new LinkedHashMap<>();
                for (String header : headers) {
                    row.put(header, record.isSet(header) ? record.get(header) : "");
                }
                rows.add(row);
            }
            return new ParsedTable(headers, rows);
        } catch (IOException ex) {
            throw new BadRequestException("Could not parse CSV file");
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Malformed CSV file: " + ex.getMessage());
        }
    }

    private ParsedTable parseXlsx(MultipartFile file) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(file.getBytes()))) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new BadRequestException("The spreadsheet has no header row");
            }
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                String value = formatter.formatCellValue(cell).trim();
                headers.add(value.isBlank() ? ("Column" + (cell.getColumnIndex() + 1)) : value);
            }
            List<Map<String, String>> rows = new ArrayList<>();
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                if (rows.size() >= MAX_ROWS) {
                    break;
                }
                Row sheetRow = sheet.getRow(rowIndex);
                if (sheetRow == null) {
                    continue;
                }
                Map<String, String> row = new LinkedHashMap<>();
                boolean hasValue = false;
                for (int col = 0; col < headers.size(); col++) {
                    Cell cell = sheetRow.getCell(col);
                    String value = cell == null ? "" : formatter.formatCellValue(cell).trim();
                    if (!value.isBlank()) {
                        hasValue = true;
                    }
                    row.put(headers.get(col), value);
                }
                if (hasValue) {
                    rows.add(row);
                }
            }
            return new ParsedTable(headers, rows);
        } catch (IOException ex) {
            throw new BadRequestException("Could not parse spreadsheet file");
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException("Malformed spreadsheet file: " + ex.getMessage());
        }
    }
}
