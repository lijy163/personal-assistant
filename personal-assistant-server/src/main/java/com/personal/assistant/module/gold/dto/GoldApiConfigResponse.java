package com.personal.assistant.module.gold.dto;

import java.time.LocalDateTime;

public record GoldApiConfigResponse(
        Long id,
        String apiName,
        String purpose,
        String endpoint,
        String authType,
        String maskedApiKey,
        int rateLimitPerMinute,
        boolean enabled,
        LocalDateTime lastTestTime,
        Boolean lastTestSuccess,
        String lastTestMessage
) {
}
