package com.personal.assistant.module.gold.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("gold_collection_result")
public class GoldCollectionResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long watchItemId;
    private Long apiConfigId;
    private Boolean success;
    private String summary;
    private String rawData;
    private String errorMessage;
    private LocalDateTime collectedAt;
}
