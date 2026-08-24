package com.personal.assistant.module.tradingreview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("trading_rule")
public class TradingRule {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private String name;
    private String category;
    private String summary;
    private String buyConditions;
    private String forbiddenConditions;
    private String sellConditions;
    private String checklist;
    private BigDecimal maxPosition;
    private Boolean enabled;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
