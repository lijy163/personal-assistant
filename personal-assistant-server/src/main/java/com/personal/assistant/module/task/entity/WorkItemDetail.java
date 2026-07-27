package com.personal.assistant.module.task.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data;
@Data @TableName("work_item_detail") public class WorkItemDetail { @TableId private Long taskId; private String workType; private String projectName; }