package com.personal.assistant.module.activity.dto;

import java.time.LocalDate;

/**
 * 单日记录条数，用于月历打点与活动网格。
 */
public record ActivityDaySummary(LocalDate logDate, long entryCount) {
}
