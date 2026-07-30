package com.personal.assistant.module.calendar.dto;
import java.time.LocalDateTime;
public record CalendarEventResponse(String key, String sourceType, Long sourceId, String title,
        LocalDateTime startAt, LocalDateTime endAt, boolean allDay, String status, String color,
        String route, String recurrenceRule, boolean workdayOnly, boolean overridden, boolean conflict) {}
