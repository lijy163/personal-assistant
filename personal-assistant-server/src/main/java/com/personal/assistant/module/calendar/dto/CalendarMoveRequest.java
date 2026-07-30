package com.personal.assistant.module.calendar.dto;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
public record CalendarMoveRequest(@NotNull LocalDateTime startAt, LocalDateTime endAt, Boolean allDay, String color) {}
