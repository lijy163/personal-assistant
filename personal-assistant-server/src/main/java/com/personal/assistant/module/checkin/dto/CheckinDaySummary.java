package com.personal.assistant.module.checkin.dto;

import java.time.LocalDate;

/**
 * 单个日期的打卡项数，用于月历打点与近一年打卡网格。
 */
public record CheckinDaySummary(LocalDate checkinDate, long doneCount) {
}
