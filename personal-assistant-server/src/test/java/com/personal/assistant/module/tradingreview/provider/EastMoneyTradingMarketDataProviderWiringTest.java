package com.personal.assistant.module.tradingreview.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class EastMoneyTradingMarketDataProviderWiringTest {
    @Test
    void springUsesAutowiredProductionConstructor() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ObjectMapper.class);
            context.register(EastMoneyTradingMarketDataProvider.class);
            context.refresh();
            assertNotNull(context.getBean(EastMoneyTradingMarketDataProvider.class));
        }
    }
}
