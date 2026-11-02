package com.personal.assistant.module.checkin.dto;

import java.time.LocalDate;

/**
 * 打卡项及其相对某个日期的完成情况。
 */
public record CheckinHabitResponse(
        Long id,
        String name,
        String icon,
        String color,
        String category,
        String note,
        Boolean active,
        Integer sortOrder,
        Boolean checked,
        Integer streak,
        Long monthCount,
        LocalDate lastCheckinDate
) {
}
