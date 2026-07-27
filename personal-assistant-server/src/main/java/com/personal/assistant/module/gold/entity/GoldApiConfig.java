package com.personal.assistant.module.gold.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("gold_api_config")
public class GoldApiConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String apiName;
    private String purpose;
    private String endpoint;
    private String authType;
    private String apiKeyEncrypted;
    private Integer rateLimitPerMinute;
    private Boolean enabled;
    private LocalDateTime lastTestTime;
    private Boolean lastTestSuccess;
    private String lastTestMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
