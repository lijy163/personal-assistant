package com.personal.assistant.module.codexagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("codex_agent")
public class CodexAgent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String tokenPrefix;
    private String tokenHash;
    private String status;
    private LocalDateTime lastSeenAt;
    private LocalDateTime revokedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
