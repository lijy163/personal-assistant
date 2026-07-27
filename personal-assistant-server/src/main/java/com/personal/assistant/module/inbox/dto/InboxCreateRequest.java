package com.personal.assistant.module.inbox.dto;import jakarta.validation.constraints.*;public record InboxCreateRequest(@NotBlank @Size(max=5000)String content){}
