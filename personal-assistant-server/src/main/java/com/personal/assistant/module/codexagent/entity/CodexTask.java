package com.personal.assistant.module.codexagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("codex_task")
public class CodexTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long agentId;
    private String projectKey;
    private String prompt;
    private String model;
    private String reasoningEffort;
    private String permissionMode;
    private String status;
    private String source;
    private String externalUserId;
    private String externalMessageId;
    private String leaseId;
    private LocalDateTime leaseExpiresAt;
    private String threadId;
    private String finalResponse;
    private String errorMessage;
    private LocalDateTime requestedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime updatedAt;
}
