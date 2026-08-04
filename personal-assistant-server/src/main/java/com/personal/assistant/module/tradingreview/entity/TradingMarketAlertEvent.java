package com.personal.assistant.module.tradingreview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("trading_market_alert_event")
public class TradingMarketAlertEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long ruleId;
    private Long reviewId;
    private LocalDate tradeDate;
    private String snapshotType;
    private String ruleType;
    private String metricKey;
    private String direction;
    private BigDecimal thresholdValue;
    private BigDecimal observedValue;
    private String sectorName;
    private String title;
    private String content;
    private String notificationStatus;
    private String notificationMessage;
    private LocalDateTime triggeredAt;
}
