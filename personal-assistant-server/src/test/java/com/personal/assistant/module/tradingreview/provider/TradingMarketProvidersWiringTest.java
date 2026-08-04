package com.personal.assistant.module.tradingreview.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TradingMarketProvidersWiringTest {
    @Test
    void springBuildsProviderChainAndSelectsRouterAsPrimary() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ObjectMapper.class);
            context.register(IfindProperties.class,
                    IfindTradingMarketDataProvider.class,
                    EastMoneyTradingMarketDataProvider.class,
                    EastMoneyBoardMetricsDataProvider.class,
                    EastMoneyDetailedMarketDataProvider.class,
                    TradingMarketDataProviderRouter.class);
            context.refresh();
            assertNotNull(context.getBean(EastMoneyTradingMarketDataProvider.class));
            assertNotNull(context.getBean(EastMoneyBoardMetricsDataProvider.class));
            assertInstanceOf(TradingMarketDataProviderRouter.class,
                    context.getBean(TradingMarketDataProvider.class));
        }
    }
}
