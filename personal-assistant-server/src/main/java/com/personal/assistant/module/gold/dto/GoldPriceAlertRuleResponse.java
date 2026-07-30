package com.personal.assistant.module.gold.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GoldPriceAlertRuleResponse(
        Long id,
        String alertKey,
        String title,
        String quoteType,
        BigDecimal threshold,
        String brandNames,
        boolean enabled,
        String condition,
        String status,
        BigDecimal lastPrice,
        LocalDateTime lastNotifiedAt,
        boolean channelConfigured,
        String checkInterval
) {
}