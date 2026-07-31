package com.personal.assistant.module.tradingreview.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TradingMarketProvidersWiringTest {
    @Test
    void springBuildsProviderChainAndSelectsDetailedProviderAsPrimary() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ObjectMapper.class);
            context.register(EastMoneyTradingMarketDataProvider.class,
                    EastMoneyBoardMetricsDataProvider.class,
                    EastMoneyDetailedMarketDataProvider.class);
            context.refresh();
            assertNotNull(context.getBean(EastMoneyTradingMarketDataProvider.class));
            assertNotNull(context.getBean(EastMoneyBoardMetricsDataProvider.class));
            assertInstanceOf(EastMoneyDetailedMarketDataProvider.class,
                    context.getBean(TradingMarketDataProvider.class));
        }
    }
}
