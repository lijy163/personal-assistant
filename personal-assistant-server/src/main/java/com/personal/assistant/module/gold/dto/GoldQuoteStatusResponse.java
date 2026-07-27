package com.personal.assistant.module.gold.dto;

import java.time.LocalDateTime;
import java.util.List;

public record GoldQuoteStatusResponse(
        int watchCount,
        int quotedCount,
        int missingQuoteCount,
        LocalDateTime lastQuoteTime,
        LocalDateTime lastRefreshTime,
        int recentSuccess,
        int recentFailed,
        List<GoldQuoteFailure> recentFailures
) {
    public record GoldQuoteFailure(
            Long watchItemId,
            String displayName,
            String errorMessage,
            LocalDateTime collectedAt
    ) {
    }
}
