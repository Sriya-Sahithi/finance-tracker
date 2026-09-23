package com.financetracker.imports;

import java.util.List;
import java.util.Map;

/** In-memory representation of a parsed CSV/XLSX file: header names and row values keyed by header. */
public record ParsedTable(List<String> headers, List<Map<String, String>> rows) {
}
