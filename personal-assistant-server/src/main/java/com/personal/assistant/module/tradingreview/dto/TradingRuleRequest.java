package com.personal.assistant.module.tradingreview.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record TradingRuleRequest(
        @NotBlank String name,
        @NotBlank String category,
        String summary,
        @NotBlank String buyConditions,
        String forbiddenConditions,
        String sellConditions,
        String checklist,
        @DecimalMin("0") BigDecimal maxPosition,
        Boolean enabled
) {
}
