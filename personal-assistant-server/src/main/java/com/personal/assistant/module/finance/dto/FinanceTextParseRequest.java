package com.personal.assistant.module.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FinanceTextParseRequest(
        @NotBlank @Size(max = 30000) String text) {
}
