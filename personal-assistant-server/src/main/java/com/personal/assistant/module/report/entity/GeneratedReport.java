package com.personal.assistant.module.report.entity;
import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.*;
@Data @TableName("generated_report") public class GeneratedReport {@TableId(type=IdType.AUTO)private Long id;private Long userId;private String reportType;private LocalDate periodStart;private LocalDate periodEnd;private String title;private String markdownContent;private LocalDateTime createdAt;}
