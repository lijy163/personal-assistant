package com.personal.assistant.module.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record FinanceTextParsePreview(
        List<Row> rows,
        int ignoredLineCount) {

    public record Row(
            int rowNumber,
            LocalDateTime transactionTime,
            String direction,
            BigDecimal amount,
            String merchant,
            String description,
            String transactionType,
            Long categoryId,
            String sourceText,
            String warning) {
    }
}
