package com.personal.assistant.module.finance.entity;
import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.*;
@Data @TableName("finance_category") public class FinanceCategory {@TableId(type=IdType.AUTO)private Long id;private Long userId;private String categoryName;private String direction;private Long parentId;private Integer sortOrder;private Boolean enabled;private LocalDateTime createdAt;}
