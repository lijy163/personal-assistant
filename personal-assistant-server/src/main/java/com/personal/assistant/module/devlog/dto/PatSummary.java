package com.personal.assistant.module.devlog.dto;

import java.time.LocalDateTime;

public record PatSummary(Long id, String name, String tokenPrefix, String scope, LocalDateTime expiresAt,
                         LocalDateTime lastUsedAt, LocalDateTime revokedAt, LocalDateTime createdAt) {
}
