package com.personal.assistant.module.system.dto;

import java.time.OffsetDateTime;

public record HealthStatusResponse(
        String status,
        String serviceName,
        String version,
        OffsetDateTime checkedAt
) {
}
