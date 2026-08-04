package com.personal.assistant.module.tradingreview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("trading_alert_event")
public class TradingAlertEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long ruleId;
    private Long watchItemId;
    private String stockCode;
    private String stockName;
    private String ruleType;
    private String direction;
    private BigDecimal thresholdValue;
    private BigDecimal observedValue;
    private BigDecimal latestPrice;
    private BigDecimal changePercent;
    private String title;
    private String content;
    private String notificationStatus;
    private String notificationMessage;
    private LocalDateTime triggeredAt;
}
