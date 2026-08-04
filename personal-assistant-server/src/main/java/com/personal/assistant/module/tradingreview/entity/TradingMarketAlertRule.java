package com.personal.assistant.module.tradingreview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("trading_market_alert_rule")
public class TradingMarketAlertRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String ruleType;
    private String snapshotType;
    private String metricKey;
    private String direction;
    private BigDecimal thresholdValue;
    private String sectorName;
    private Integer sectorLevel;
    private String title;
    private String note;
    private Boolean enabled;
    private Boolean onceOnly;
    private String status;
    private LocalDate validFrom;
    private LocalDate validTo;
    private LocalDateTime lastCheckedAt;
    private LocalDateTime lastTriggeredAt;
    private BigDecimal lastObservedValue;
    private Long lastReviewId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
