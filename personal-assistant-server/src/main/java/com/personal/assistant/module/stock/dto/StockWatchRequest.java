package com.personal.assistant.module.stock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockWatchRequest(
        @NotBlank @Size(max = 40) String stockCode,
        @NotBlank @Size(max = 100) String stockName,
        @NotBlank String market,
        @Size(max = 100) String industry,
        BigDecimal latestPrice,
        BigDecimal changePercent,
        BigDecimal marketValue,
        LocalDateTime quoteTime,
        @Size(max = 1000) String tags,
        @Size(max = 5000) String reason,
        @Size(max = 5000) String remark,
        Boolean enabled
) {
}