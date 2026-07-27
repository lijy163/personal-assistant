package com.personal.assistant.module.devlog.dto;

import java.time.LocalDateTime;

public record PatCreateResponse(Long id, String name, String token, String scope, LocalDateTime expiresAt,
                                LocalDateTime createdAt) {
}
