package com.personal.assistant.module.checkin.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 打卡页主数据：某天的打卡项状态、统计口径与当天明细，一次请求取全。
 */
public record CheckinOverview(
        LocalDate date,
        int habitCount,
        int doneCount,
        int currentStreak,
        long monthCount,
        long totalDays,
        List<CheckinHabitResponse> habits,
        List<CheckinRecordResponse> records
) {
}
