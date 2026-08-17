package com.personal.assistant.module.task.dto;
import jakarta.validation.constraints.*; import java.time.LocalDateTime;
public record TaskUpsertRequest(
 @NotBlank(message="标题不能为空") @Size(max=200,message="标题不超过 200 字") String title,
 @NotNull(message="事项类型不能为空") TaskType itemType,
 @NotNull(message="优先级不能为空") TaskPriority priority,
 @NotNull(message="状态不能为空") TaskStatus status,
 LocalDateTime planTime, LocalDateTime deadline, Boolean reminderEnabled, LocalDateTime reminderAt,
 @Size(max=1000,message="标签不超过 1000 字") String tags,
 @Size(max=5000,message="备注不超过 5000 字") String remark,
 @Size(max=50,message="生活分类不超过 50 字") String category,
 @Size(max=50,message="工作类型不超过 50 字") String workType,
 @Size(max=100,message="项目名称不超过 100 字") String projectName) {}
