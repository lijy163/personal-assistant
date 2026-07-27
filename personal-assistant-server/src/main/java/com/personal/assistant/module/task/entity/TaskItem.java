package com.personal.assistant.module.task.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("task_item") public class TaskItem {
 @TableId(type=IdType.AUTO) private Long id; private Long userId; private String title; private String itemType; private String priority; private String status; private LocalDateTime planTime; private LocalDateTime deadline; private Boolean reminderEnabled; private String tags; private String remark; private Boolean archived; private LocalDateTime statusChangedAt; private LocalDateTime createdAt; private LocalDateTime updatedAt;
}