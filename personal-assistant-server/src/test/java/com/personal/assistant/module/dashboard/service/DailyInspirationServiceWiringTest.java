package com.personal.assistant.module.dashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class DailyInspirationServiceWiringTest {
    @Test
    void springUsesAutowiredProductionConstructor() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ObjectMapper.class);
            context.register(DailyInspirationService.class);
            context.refresh();
            assertNotNull(context.getBean(DailyInspirationService.class));
        }
    }
}
