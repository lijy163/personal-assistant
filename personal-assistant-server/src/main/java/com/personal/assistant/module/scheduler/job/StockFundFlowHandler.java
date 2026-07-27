package com.personal.assistant.module.scheduler.job;

import com.personal.assistant.module.stock.dto.StockFundFlowRefreshResponse;
import com.personal.assistant.module.stock.service.StockFundFlowService;
import org.springframework.stereotype.Component;

@Component
public class StockFundFlowHandler implements JobHandler {
    private final StockFundFlowService service;

    public StockFundFlowHandler(StockFundFlowService service) { this.service = service; }
    @Override public String type() { return "STOCK_FUND_FLOW"; }
    @Override public String execute(String config) {
        StockFundFlowRefreshResponse result = service.refreshForScheduler();
        return "股票资金流刷新完成：总数 " + result.total() + "，成功 " + result.success() + "，失败 " + result.failed();
    }
}
