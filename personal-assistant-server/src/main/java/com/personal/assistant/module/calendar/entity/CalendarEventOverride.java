package com.personal.assistant.module.calendar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("calendar_event_override")
public class CalendarEventOverride {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private String sourceType;
    private Long sourceId;
    private String titleOverride;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Boolean allDay;
    private String color;
    private String recurrenceRule;
    private Boolean workdayOnly;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
