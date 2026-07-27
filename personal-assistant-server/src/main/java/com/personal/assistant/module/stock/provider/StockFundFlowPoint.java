package com.personal.assistant.module.stock.provider;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockFundFlowPoint(
        BigDecimal mainNetInflow, BigDecimal mainNetRatio,
        BigDecimal superLargeNetInflow, BigDecimal superLargeNetRatio,
        BigDecimal largeNetInflow, BigDecimal largeNetRatio,
        BigDecimal mediumNetInflow, BigDecimal mediumNetRatio,
        BigDecimal smallNetInflow, BigDecimal smallNetRatio,
        BigDecimal latestPrice, BigDecimal changePercent, BigDecimal turnoverAmount,
        LocalDateTime quoteTime
) {
}
