package com.personal.assistant.module.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 新增活动记录。除内容外全部可选，不传即按当前时间与默认分类落库。
 */
public record ActivityLogCreateRequest(
        @NotBlank(message = "记录内容不能为空")
        @Size(max = 5000, message = "记录内容不超过 5000 字")
        String content,

        LocalDate logDate,

        LocalDateTime occurredAt,

        String category,

        @Size(max = 500, message = "标签不超过 500 字")
        String tags,

        String source
) {
}
