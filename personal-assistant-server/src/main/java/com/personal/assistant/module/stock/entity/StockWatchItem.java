package com.personal.assistant.module.stock.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("stock_watch_item")
public class StockWatchItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String stockCode;
    private String stockName;
    private String market;
    private String industry;
    private BigDecimal latestPrice;
    private BigDecimal changePercent;
    private BigDecimal marketValue;
    private LocalDateTime quoteTime;
    private String tags;
    private String reason;
    private String remark;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}