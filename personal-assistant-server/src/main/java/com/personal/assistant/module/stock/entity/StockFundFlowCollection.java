package com.personal.assistant.module.stock.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("stock_fund_flow_collection")
public class StockFundFlowCollection {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId; private Long watchItemId; private Boolean success; private Integer snapshotCount;
    private String provider; private String errorMessage; private LocalDateTime collectedAt;
}
