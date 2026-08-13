package com.personal.assistant.module.codexagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("codex_task_event")
public class CodexTaskEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String eventType;
    private String content;
    private LocalDateTime createdAt;
}
