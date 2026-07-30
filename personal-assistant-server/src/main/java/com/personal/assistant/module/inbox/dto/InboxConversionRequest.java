package com.personal.assistant.module.inbox.dto;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record InboxConversionRequest(@NotBlank String type, String title, String taskType, String priority,
 String category, String workType, String projectName, LocalDateTime planTime, LocalDateTime deadline,
 LocalDateTime remindTime, Long channelId, Long accountId, Long categoryId, BigDecimal amount,
 String direction, LocalDateTime transactionTime, Long learningPlanId, Integer durationMinutes, String note) {}
