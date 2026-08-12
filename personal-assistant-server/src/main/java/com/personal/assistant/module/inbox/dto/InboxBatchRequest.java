package com.personal.assistant.module.inbox.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record InboxBatchRequest(
        @NotEmpty @Size(max = 200) List<Long> ids
) {
}
