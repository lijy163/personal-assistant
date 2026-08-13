package com.personal.assistant.module.publiccodex;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class PublicCodexDtos {
    private PublicCodexDtos() {
    }

    public record AskRequest(@NotBlank @Size(max = 2000) String question) {
    }

    public record AskResponse(Long taskId, String status, LocalDateTime createdAt) {
    }

    public record AnswerResponse(Long taskId, String question, String status, String answer,
                                 String errorMessage, LocalDateTime createdAt, LocalDateTime finishedAt) {
    }
}
