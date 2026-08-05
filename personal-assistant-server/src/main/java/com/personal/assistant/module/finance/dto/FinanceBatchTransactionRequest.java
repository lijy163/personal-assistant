package com.personal.assistant.module.finance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record FinanceBatchTransactionRequest(
        @NotEmpty @Size(max = 200) List<@Valid FinanceTransactionRequest> transactions) {
}
