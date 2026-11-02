package com.personal.assistant.module.checkin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 打卡记录，同一用户同一天同一个打卡项只会有一条。
 * activityLogId 指向打卡时自动写入的活动记录，撤销打卡时按它回滚。
 */
@Data
@TableName("habit_checkin")
public class HabitCheckin {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long habitId;

    private LocalDate checkinDate;

    private BigDecimal value;

    private String note;

    /**
     * 打卡入口：CHECKIN（打卡页）/ MANUAL（生活管理页手工补录）。
     */
    private String source;

    private Long activityLogId;

    private LocalDateTime createdAt;
}
