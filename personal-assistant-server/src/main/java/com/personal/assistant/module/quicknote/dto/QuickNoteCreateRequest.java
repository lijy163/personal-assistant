package com.personal.assistant.module.quicknote.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QuickNoteCreateRequest(
        @NotBlank(message = "记录内容不能为空")
        @Size(max = 2000, message = "记录内容不超过 2000 字")
        String content
) {
}
