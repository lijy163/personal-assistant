package com.personal.assistant.module.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record StockMarketMapResponse(
        String market,
        String scope,
        LocalDateTime generatedAt,
        StockMarketMapStats stats,
        List<StockIndustryNode> industries
) {
    public record StockMarketMapStats(
            int total,
            int up,
            int flat,
            int down,
            BigDecimal averageChangePercent
    ) {
    }

    public record StockIndustryNode(
            String industry,
            int count,
            BigDecimal averageChangePercent,
            BigDecimal totalMarketValue,
            List<StockMapItem> children
    ) {
    }

    public record StockMapItem(
            Long id,
            String stockCode,
            String stockName,
            String market,
            String industry,
            BigDecimal latestPrice,
            BigDecimal changePercent,
            BigDecimal marketValue,
            BigDecimal weight,
            LocalDateTime quoteTime,
            String tags
    ) {
    }
}