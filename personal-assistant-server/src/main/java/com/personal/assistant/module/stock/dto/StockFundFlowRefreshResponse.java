package com.personal.assistant.module.stock.dto;

import java.time.LocalDateTime;
import java.util.List;

public record StockFundFlowRefreshResponse(int total, int success, int failed, LocalDateTime refreshedAt,
                                           List<Item> items) {
    public record Item(Long watchItemId, String stockCode, String stockName, boolean success,
                       int snapshotCount, String message) {
    }
}
