package com.personal.assistant.module.activity.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ActivityLogResponse(
        Long id,
        String content,
        LocalDate logDate,
        LocalDateTime occurredAt,
        String category,
        String tags,
        String source,
        Boolean pinned,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
