package com.personal.assistant.module.finance.entity;
import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.*;
@Data @TableName("finance_account") public class FinanceAccount {@TableId(type=IdType.AUTO)private Long id;private Long userId;private String accountName;private String accountType;private String institution;private String currency;private Boolean enabled;private LocalDateTime createdAt;private LocalDateTime updatedAt;}
