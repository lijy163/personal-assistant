package com.personal.assistant.module.tradingreview.dto;

import java.math.BigDecimal;

public record TradeMetrics(
        BigDecimal buyQuantity,
        BigDecimal sellQuantity,
        BigDecimal remainingQuantity,
        BigDecimal averageCost,
        BigDecimal realizedProfit,
        BigDecimal unrealizedProfit,
        BigDecimal totalFees,
        BigDecimal returnRate,
        Integer holdingDays) {
}
