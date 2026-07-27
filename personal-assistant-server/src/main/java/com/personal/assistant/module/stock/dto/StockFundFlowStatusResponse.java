package com.personal.assistant.module.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record StockFundFlowStatusResponse(int watchCount, int coveredCount, int missingCount,
                                          BigDecimal coverageRate, LocalDateTime lastQuoteTime,
                                          LocalDateTime lastRefreshTime, int recentSuccess, int recentFailed,
                                          List<Failure> recentFailures) {
    public record Failure(Long watchItemId, String stockName, String errorMessage, LocalDateTime collectedAt) {
    }
}
