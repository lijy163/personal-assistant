package com.personal.assistant.module.gold.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record GoldPublicQuoteResponse(
        List<Quote> quotes,
        BigDecimal usdCny,
        LocalDateTime quoteTime,
        LocalDateTime fetchedAt,
        String source,
        int refreshIntervalSeconds
) {
    public record Quote(
            String code,
            String displayName,
            BigDecimal price,
            String unit,
            String description,
            boolean converted
    ) {
    }
}
