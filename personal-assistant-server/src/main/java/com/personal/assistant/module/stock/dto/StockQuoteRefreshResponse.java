package com.personal.assistant.module.stock.dto;

import java.time.LocalDateTime;
import java.util.List;

public record StockQuoteRefreshResponse(
        int total,
        int success,
        int failed,
        LocalDateTime refreshedAt,
        List<StockQuoteRefreshItem> items
) {
    public record StockQuoteRefreshItem(
            Long watchItemId,
            String stockCode,
            String stockName,
            boolean success,
            String message
    ) {
    }
}