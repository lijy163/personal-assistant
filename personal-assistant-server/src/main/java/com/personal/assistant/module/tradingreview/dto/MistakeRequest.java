package com.personal.assistant.module.tradingreview.dto;
import jakarta.validation.constraints.*;import java.time.*;
public record MistakeRequest(Long tradeLogId,@NotNull LocalDate occurredDate,@NotBlank String category,@NotBlank String title,String description,String rootCause,String correction,String status,@Min(1) Integer repeatCount){}