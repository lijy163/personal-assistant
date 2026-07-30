package com.personal.assistant.module.calendar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("calendar_custom_event")
public class CalendarCustomEvent {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private String title;
    private String description;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Boolean allDay;
    private String status;
    private String color;
    private String recurrenceRule;
    private Boolean workdayOnly;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
