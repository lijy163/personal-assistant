package com.personal.assistant.module.auth.service;

import com.personal.assistant.common.security.JwtTokenService;
import com.personal.assistant.module.auth.mapper.UserAccountMapper;
import com.personal.assistant.module.security.service.SecurityAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class AuthServiceWiringTest {
    @Test
    void springUsesAutowiredProductionConstructor() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(UserAccountMapper.class, () -> mock(UserAccountMapper.class));
            context.registerBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class));
            context.registerBean(JwtTokenService.class, () -> mock(JwtTokenService.class));
            context.registerBean(SecurityAuditService.class, () -> mock(SecurityAuditService.class));
            context.register(AuthService.class);
            context.refresh();
            assertNotNull(context.getBean(AuthService.class));
        }
    }
}
