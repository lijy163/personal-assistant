package com.personal.assistant.module.gold.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("gold_watch_item")
public class GoldWatchItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String goldType;
    private String brandName;
    private String displayName;
    private String unit;
    private BigDecimal latestPrice;
    private BigDecimal changeAmount;
    private BigDecimal changePercent;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal openPrice;
    private BigDecimal previousClose;
    private BigDecimal buyPrice;
    private BigDecimal sellPrice;
    private LocalDateTime quoteTime;
    private String sourceName;
    private String sourceUrl;
    private String remark;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
