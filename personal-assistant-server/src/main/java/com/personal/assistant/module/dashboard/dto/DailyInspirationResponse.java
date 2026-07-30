package com.personal.assistant.module.dashboard.dto;

import java.time.LocalDate;

public record DailyInspirationResponse(
        String content,
        String translation,
        String imageUrl,
        LocalDate date,
        String source
) {
}
