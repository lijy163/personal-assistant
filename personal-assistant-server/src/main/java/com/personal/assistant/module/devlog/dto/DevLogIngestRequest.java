package com.personal.assistant.module.devlog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record DevLogIngestRequest(
        @NotBlank @Size(max = 64) String fingerprint,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 120) String projectName,
        @Size(max = 500) String repository,
        @Size(max = 200) String branchName,
        @Size(max = 64) String commitHash,
        @NotBlank @Size(max = 10000) String taskGoal,
        @NotBlank @Size(max = 30000) String coreChanges,
        @Size(max = 30000) String technicalDecisions,
        @Size(max = 30000) String problemSolution,
        @Size(max = 30000) String verificationResult,
        @Size(max = 1000) String tags,
        @Size(max = 40) String source,
        LocalDateTime occurredAt,
        @NotBlank @Size(max = 100000) String markdownContent
) {
}
