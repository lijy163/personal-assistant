package com.personal.assistant.module.gold.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("gold_price_alert_state")
public class GoldPriceAlertState {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String alertKey;
    private Boolean belowThreshold;
    private BigDecimal lastPrice;
    private LocalDateTime lastNotifiedAt;
    private LocalDateTime updatedAt;
}
