package com.personal.assistant.module.gold.dto;

import java.time.LocalDateTime;
import java.util.List;

public record GoldQuoteRefreshResponse(
        int total,
        int success,
        int failed,
        LocalDateTime refreshedAt,
        List<GoldQuoteRefreshItem> items
) {
    public record GoldQuoteRefreshItem(
            Long watchItemId,
            String displayName,
            String goldType,
            boolean success,
            String message
    ) {
    }
}
