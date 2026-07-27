package com.personal.assistant.module.stock.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("stock_fund_flow_snapshot")
public class StockFundFlowSnapshot {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId; private Long watchItemId; private String stockCode; private String market;
    private BigDecimal mainNetInflow; private BigDecimal mainNetRatio;
    private BigDecimal superLargeNetInflow; private BigDecimal superLargeNetRatio;
    private BigDecimal largeNetInflow; private BigDecimal largeNetRatio;
    private BigDecimal mediumNetInflow; private BigDecimal mediumNetRatio;
    private BigDecimal smallNetInflow; private BigDecimal smallNetRatio;
    private BigDecimal latestPrice; private BigDecimal changePercent; private BigDecimal turnoverAmount;
    private String provider; private String periodType; private LocalDateTime quoteTime; private LocalDateTime collectedAt;
}
