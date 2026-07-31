package com.personal.assistant.module.tradingreview.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TradingMarketProvidersWiringTest {
    @Test
    void springBuildsDecoratorAndSelectsItAsPrimaryProvider() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ObjectMapper.class);
            context.register(EastMoneyTradingMarketDataProvider.class, EastMoneyBoardMetricsDataProvider.class);
            context.refresh();
            assertNotNull(context.getBean(EastMoneyTradingMarketDataProvider.class));
            assertInstanceOf(EastMoneyBoardMetricsDataProvider.class,
                    context.getBean(TradingMarketDataProvider.class));
        }
    }
}
