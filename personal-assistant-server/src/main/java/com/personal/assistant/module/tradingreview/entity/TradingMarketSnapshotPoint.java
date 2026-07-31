package com.personal.assistant.module.tradingreview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("trading_market_snapshot_point")
public class TradingMarketSnapshotPoint {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId; private LocalDate tradeDate; private String snapshotType; private LocalDateTime quoteTime;
    private BigDecimal sentimentScore; private String marketStage; private Integer risingCount; private Integer fallingCount;
    private Integer limitUpCount; private Integer limitDownCount; private BigDecimal brokenBoardRate; private Integer maxStreak;
    private BigDecimal turnoverAmount; private String rawMetrics; private LocalDateTime createdAt;
}
