package com.personal.assistant.module.finance.service;

import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class BillFileParser {
    public List<Map<String, String>> parse(String fileName, byte[] bytes) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) return parseExcel(bytes);
        if (lower.endsWith(".csv")) return parseCsv(bytes);
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "仅支持 CSV、XLS 和 XLSX 账单文件");
    }

    private List<Map<String, String>> parseExcel(byte[] bytes) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0); DataFormatter formatter = new DataFormatter();
            List<String> headers = null; List<Map<String, String>> result = new ArrayList<>();
            for (Row row : sheet) {
                List<String> values = new ArrayList<>();
                for (int column = 0; column < row.getLastCellNum(); column++) values.add(formatter.formatCellValue(row.getCell(column)).trim());
                if (headers == null && looksLikeHeader(values)) { headers = values; continue; }
                if (headers != null && values.stream().anyMatch(value -> !value.isBlank())) result.add(toMap(headers, values));
            }
            if (headers == null) throw new IllegalArgumentException("未找到交易明细表头");
            return result;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Excel 账单解析失败：" + exception.getMessage());
        }
    }

    private List<Map<String, String>> parseCsv(byte[] bytes) {
        Charset charset = detectCharset(bytes); String text = new String(bytes, charset).replace("\uFEFF", "");
        List<List<String>> lines = parseCsvRows(text); List<String> headers = null; List<Map<String, String>> result = new ArrayList<>();
        for (List<String> values : lines) {
            List<String> trimmed = values.stream().map(String::trim).toList();
            if (headers == null && looksLikeHeader(trimmed)) { headers = trimmed; continue; }
            if (headers != null && trimmed.stream().anyMatch(value -> !value.isBlank())) result.add(toMap(headers, trimmed));
        }
        if (headers == null) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "CSV 中未找到交易明细表头");
        return result;
    }

    private List<List<String>> parseCsvRows(String text) {
        List<List<String>> rows = new ArrayList<>(); List<String> row = new ArrayList<>(); StringBuilder value = new StringBuilder(); boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '"') { if (quoted && index + 1 < text.length() && text.charAt(index + 1) == '"') { value.append('"'); index++; } else quoted = !quoted; }
            else if (current == ',' && !quoted) { row.add(value.toString()); value.setLength(0); }
            else if ((current == '\n' || current == '\r') && !quoted) { if (current == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') index++; row.add(value.toString()); value.setLength(0); rows.add(row); row = new ArrayList<>(); }
            else value.append(current);
        }
        if (value.length() > 0 || !row.isEmpty()) { row.add(value.toString()); rows.add(row); }
        return rows;
    }

    private boolean looksLikeHeader(List<String> values) {
        String joined = String.join("|", values);
        return (joined.contains("交易时间") || joined.contains("交易日期") || joined.contains("记账日期"))
                && (joined.contains("金额") || joined.contains("收/支") || joined.contains("收入") || joined.contains("支出"));
    }

    private Map<String, String> toMap(List<String> headers, List<String> values) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index++) result.put(headers.get(index), index < values.size() ? values.get(index) : "");
        return result;
    }

    private Charset detectCharset(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        long replacements = utf8.chars().filter(value -> value == 0xfffd).count();
        return replacements > 2 ? Charset.forName("GB18030") : StandardCharsets.UTF_8;
    }
}
