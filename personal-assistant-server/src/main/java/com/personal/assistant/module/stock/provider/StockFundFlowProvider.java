package com.personal.assistant.module.stock.provider;

import com.personal.assistant.module.stock.entity.StockWatchItem;

import java.util.List;

public interface StockFundFlowProvider {
    String name();
    List<StockFundFlowPoint> fetchDaily(StockWatchItem item, int limit);
}
