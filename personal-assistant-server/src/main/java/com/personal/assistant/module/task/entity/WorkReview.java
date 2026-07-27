package com.personal.assistant.module.task.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("work_review") public class WorkReview { @TableId(type=IdType.AUTO) private Long id; private Long taskId; private Long userId; private String content; private String resultType; private LocalDateTime createdAt; }