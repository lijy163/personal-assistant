package com.personal.assistant.module.stock.dto;

import java.time.LocalDateTime;
import java.util.List;

public record StockQuoteStatusResponse(
        int watchCount,
        int quotedCount,
        int missingQuoteCount,
        LocalDateTime lastQuoteTime,
        LocalDateTime lastRefreshTime,
        int recentSuccess,
        int recentFailed,
        List<StockQuoteFailure> recentFailures
) {
    public record StockQuoteFailure(
            Long watchItemId,
            String stockName,
            String errorMessage,
            LocalDateTime collectedAt
    ) {
    }
}