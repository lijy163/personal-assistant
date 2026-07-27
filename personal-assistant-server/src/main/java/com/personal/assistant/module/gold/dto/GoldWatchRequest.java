package com.personal.assistant.module.gold.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GoldWatchRequest(
        @NotBlank String goldType,
        @Size(max = 100) String brandName,
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Size(max = 30) String unit,
        BigDecimal latestPrice,
        BigDecimal changeAmount,
        BigDecimal changePercent,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal openPrice,
        BigDecimal previousClose,
        BigDecimal buyPrice,
        BigDecimal sellPrice,
        LocalDateTime quoteTime,
        @Size(max = 100) String sourceName,
        @Size(max = 1000) String sourceUrl,
        @Size(max = 5000) String remark,
        Boolean enabled
) {
}
