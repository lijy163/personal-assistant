package com.personal.assistant.module.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FinanceTransactionRequest(
        @NotNull Long accountId,
        Long categoryId,
        @NotNull LocalDateTime transactionTime,
        @NotBlank String direction,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 16, fraction = 2) BigDecimal amount,
        @Size(max = 255) String merchant,
        @Size(max = 2000) String description,
        @Size(max = 30) String transactionType,
        @Size(max = 2000) String note) {
}
