package com.personal.assistant.module.tradingreview.dto;

public record TradingRuleStatsResponse(Long ruleId, long totalTrades, long plannedTrades, long openTrades, long closedTrades) {
}
