package com.personal.assistant.module.gold.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GoldApiConfigRequest(
        @NotBlank @Size(max = 100) String apiName,
        @Size(max = 200) String purpose,
        @NotBlank @Size(max = 1000) String endpoint,
        @NotBlank String authType,
        String apiKey,
        @NotNull @Min(1) @Max(10000) Integer rateLimitPerMinute,
        Boolean enabled
) {
}
