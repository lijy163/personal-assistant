package com.personal.assistant.module.devlog.dto;

import java.time.LocalDateTime;

public record DevLogSummary(Long id, String title, String projectName, String branchName, String commitHash,
                            String tags, String source, LocalDateTime occurredAt, LocalDateTime createdAt) {
}
