package com.personal.assistant.module.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record StockFundFlowOverviewResponse(
        String provider, LocalDateTime latestQuoteTime, int watchCount, int coveredCount,
        BigDecimal coverageRate, BigDecimal totalMainNetInflow, int inflowCount, int outflowCount,
        List<RankingItem> ranking
) {
    public record RankingItem(Long watchItemId, String stockCode, String stockName,
                              BigDecimal mainNetInflow, BigDecimal mainNetRatio,
                              BigDecimal superLargeNetInflow, BigDecimal largeNetInflow,
                              BigDecimal mediumNetInflow, BigDecimal smallNetInflow,
                              BigDecimal changePercent, LocalDateTime quoteTime) {
    }
}
