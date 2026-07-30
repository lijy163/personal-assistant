package com.personal.assistant.module.gold.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record GoldPriceAlertRuleRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank String quoteType,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal threshold,
        @Size(max = 500) String brandNames,
        Boolean enabled
) {
}