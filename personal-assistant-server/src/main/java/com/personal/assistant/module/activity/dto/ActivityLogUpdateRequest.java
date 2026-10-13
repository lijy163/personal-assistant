package com.personal.assistant.module.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 更新活动记录。归属日期与发生时刻不可清空，传空表示保持原值。
 */
public record ActivityLogUpdateRequest(
        @NotBlank(message = "记录内容不能为空")
        @Size(max = 5000, message = "记录内容不超过 5000 字")
        String content,

        LocalDate logDate,

        LocalDateTime occurredAt,

        String category,

        @Size(max = 500, message = "标签不超过 500 字")
        String tags,

        Boolean pinned
) {
}
