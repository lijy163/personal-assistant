package com.personal.assistant.module.scheduler.job;

import com.personal.assistant.module.gold.dto.GoldQuoteRefreshResponse;
import com.personal.assistant.module.gold.service.GoldService;
import org.springframework.stereotype.Component;

@Component
public class GoldCollectHandler implements JobHandler {
    private final GoldService service;

    public GoldCollectHandler(GoldService service) {
        this.service = service;
    }

    public String type() {
        return "GOLD_COLLECT";
    }

    public String execute(String config) {
        GoldQuoteRefreshResponse result = service.refreshEnabledQuotesForScheduler();
        return "Gold quote refresh finished: total " + result.total() + ", success " + result.success() + ", failed " + result.failed();
    }
}
