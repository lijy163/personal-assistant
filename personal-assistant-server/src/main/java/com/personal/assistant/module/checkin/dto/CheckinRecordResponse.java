package com.personal.assistant.module.checkin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 某天的打卡明细，activityLogId 可用于跳转或核对同步生成的活动记录。
 */
public record CheckinRecordResponse(
        Long id,
        Long habitId,
        String habitName,
        String icon,
        String color,
        LocalDate checkinDate,
        String note,
        String source,
        Long activityLogId,
        LocalDateTime createdAt
) {
}
