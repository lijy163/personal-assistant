package com.personal.assistant.module.quicknote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 快速记录：任意页面随手记一条，后续可归类到事项/学习/股票等模块。
 */
@Data
@TableName("quick_note")
public class QuickNote {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String content;

    /**
     * 处理状态：PENDING 待整理 / ARCHIVED 已归档 / CONVERTED 已转为正式记录。
     */
    private String status;

    /**
     * 转化后的目标类型，例如 LIFE / WORK / LEARNING / STOCK，可为空。
     */
    private String convertedType;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
