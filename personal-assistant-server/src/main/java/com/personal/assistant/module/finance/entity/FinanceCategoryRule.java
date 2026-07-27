package com.personal.assistant.module.finance.entity;
import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.*;
@Data @TableName("finance_category_rule") public class FinanceCategoryRule {@TableId(type=IdType.AUTO)private Long id;private Long userId;private String ruleName;private String keyword;private Long categoryId;private Integer priority;private Boolean enabled;private LocalDateTime createdAt;}
