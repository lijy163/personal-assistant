package com.personal.assistant.module.devlog.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record PatCreateRequest(@NotBlank @Size(max = 100) String name, @Future LocalDateTime expiresAt) {
}
