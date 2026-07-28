package com.personal.assistant.module.inbox.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record InboxCollectRequest(
        @Size(max = 5000) String content,
        @Size(max = 500) String tags,
        @Size(max = 2000) String remark,
        @Size(max = 30) String source,
        LocalDateTime recordedAt
) {
}
