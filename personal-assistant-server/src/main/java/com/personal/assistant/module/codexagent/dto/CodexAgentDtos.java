package com.personal.assistant.module.codexagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class CodexAgentDtos {
    private CodexAgentDtos() {
    }

    public record CreateAgentRequest(@NotBlank @Size(max = 100) String name) {
    }

    public record CreatedAgent(Long id, String name, String token, LocalDateTime createdAt) {
    }

    public record AgentSummary(Long id, String name, String model, String reasoningEffort, String tokenPrefix, String status,
                               LocalDateTime lastSeenAt, LocalDateTime revokedAt, LocalDateTime createdAt) {
    }

    public record UpdateAgentModelRequest(@Size(max = 100) String model, @Size(max = 20) String reasoningEffort) {
    }

    public record CreateTaskRequest(@NotNull Long agentId, @NotBlank @Size(max = 100) String projectKey,
                                    @NotBlank @Size(max = 20000) String prompt, @NotBlank String permissionMode) {
    }

    public record TaskSummary(Long id, Long agentId, String agentName, String projectKey, String prompt, String model,
                              String reasoningEffort,
                              String permissionMode, String status, String threadId, String finalResponse,
                              String errorMessage, LocalDateTime requestedAt, LocalDateTime startedAt,
                              LocalDateTime finishedAt, LocalDateTime updatedAt) {
    }

    public record TaskEventSummary(Long id, String eventType, String content, LocalDateTime createdAt) {
    }

    public record ClaimedTask(Long taskId, String leaseId, LocalDateTime leaseExpiresAt,
                              String projectKey, String prompt, String permissionMode, String model, String reasoningEffort) {
    }

    public record LeaseRequest(@NotBlank String leaseId) {
    }

    public record EventRequest(@NotBlank String leaseId, @NotBlank @Size(max = 80) String eventType,
                               @NotBlank @Size(max = 60000) String content) {
    }

    public record CompleteRequest(@NotBlank String leaseId, String threadId,
                                  @NotBlank @Size(max = 200000) String finalResponse) {
    }

    public record FailRequest(@NotBlank String leaseId, String threadId,
                              @NotBlank @Size(max = 20000) String errorMessage) {
    }
}
