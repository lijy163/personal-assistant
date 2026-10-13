package com.personal.assistant.module.activity.dto;

/**
 * 标签及其出现次数，用于筛选条与标签联想。
 */
public record ActivityTagSummary(String tag, long tagCount) {
}
