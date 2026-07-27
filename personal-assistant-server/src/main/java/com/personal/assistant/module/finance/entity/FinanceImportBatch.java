package com.personal.assistant.module.finance.entity;
import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.*;
@Data @TableName("finance_import_batch") public class FinanceImportBatch {@TableId(type=IdType.AUTO)private Long id;private Long userId;private Long accountId;private String platform;private String fileName;private String fileHash;private String status;private Integer totalCount;private Integer duplicateCount;private Integer importedCount;private String errorMessage;private LocalDateTime createdAt;private LocalDateTime confirmedAt;}
