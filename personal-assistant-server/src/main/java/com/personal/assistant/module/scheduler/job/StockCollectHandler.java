package com.personal.assistant.module.scheduler.job;

import com.personal.assistant.module.stock.dto.StockQuoteRefreshResponse;
import com.personal.assistant.module.stock.service.StockService;
import org.springframework.stereotype.Component;

@Component
public class StockCollectHandler implements JobHandler {
    private final StockService service;

    public StockCollectHandler(StockService service) {
        this.service = service;
    }

    public String type() {
        return "STOCK_COLLECT";
    }

    public String execute(String config) {
        StockQuoteRefreshResponse result = service.refreshEnabledQuotesForScheduler();
        return "股票行情刷新完成：总数 " + result.total() + "，成功 " + result.success() + "，失败 " + result.failed();
    }
}