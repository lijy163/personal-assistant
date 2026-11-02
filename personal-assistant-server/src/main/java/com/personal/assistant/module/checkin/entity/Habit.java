package com.personal.assistant.module.checkin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 打卡项。沿用生活管理里的 habit 表，打卡页只用到名称、图标、主题色与分类等展示字段。
 */
@Data
@TableName("habit")
public class Habit {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    /**
     * DAILY / WEEKLY，打卡页只支持每日一次。
     */
    private String frequency;

    private Integer targetCount;

    private String metricType;

    private BigDecimal targetValue;

    private String unit;

    /**
     * 停用后不再出现在今日打卡区，历史打卡与活动记录保留。
     */
    private Boolean active;

    private Long reminderId;

    /**
     * 打卡项图标，存单个 emoji 或 1 至 2 个字符，可为空。
     */
    private String icon;

    /**
     * 卡片主题色，形如 #2563eb。
     */
    private String color;

    /**
     * 同步活动记录时使用的分类：WORK / STUDY / EXERCISE / FOOD / SOCIAL / REST / OTHER。
     */
    private String category;

    private String note;

    private Integer sortOrder;

    private LocalDateTime createdAt;
}
