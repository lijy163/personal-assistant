package com.personal.assistant.module.checkin.dto;

import java.time.LocalDate;

/**
 * 打卡或撤销后的结果，前端据此刷新选中日期的打卡项状态。
 */
public record CheckinResult(
        Long habitId,
        LocalDate checkinDate,
        boolean checked,
        int streak,
        Long activityLogId
) {
}
