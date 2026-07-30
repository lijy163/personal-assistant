package com.personal.assistant.module.gold.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GoldPriceAlertRuleResponse(
        String alertKey,
        String title,
        String condition,
        String status,
        BigDecimal lastPrice,
        LocalDateTime lastNotifiedAt,
        boolean channelConfigured,
        String checkInterval
) {
}
