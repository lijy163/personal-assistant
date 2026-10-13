package com.personal.assistant.module.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日活动记录：如实记下每天做了什么事情，属于终态事实记录，不存在待整理或待转换状态。
 */
@Data
@TableName("activity_log")
public class ActivityLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /**
     * 记录正文，是唯一必填字段。
     */
    private String content;

    /**
     * 归属日期，用于按天回溯与日历聚合，允许与 occurredAt 的日期不同（支持补记）。
     */
    private LocalDate logDate;

    /**
     * 事件发生时刻。
     */
    private LocalDateTime occurredAt;

    /**
     * 可选分类：WORK / STUDY / EXERCISE / FOOD / SOCIAL / REST / OTHER。
     */
    private String category;

    /**
     * 逗号分隔的标签，可为空。
     */
    private String tags;

    /**
     * 录入来源：WEB / MOBILE_WEB / API / AGENT。
     */
    private String source;

    private Boolean pinned;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
