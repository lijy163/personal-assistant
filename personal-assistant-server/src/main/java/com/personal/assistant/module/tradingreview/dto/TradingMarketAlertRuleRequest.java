package com.personal.assistant.module.tradingreview.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TradingMarketAlertRuleRequest(
        @NotBlank String ruleType,
        String snapshotType,
        @NotBlank String metricKey,
        @NotBlank String direction,
        @NotNull @DecimalMin("0") BigDecimal thresholdValue,
        String sectorName,
        Integer sectorLevel,
        String title,
        String note,
        Boolean enabled,
        Boolean onceOnly,
        LocalDate validFrom,
        LocalDate validTo
) {
}
