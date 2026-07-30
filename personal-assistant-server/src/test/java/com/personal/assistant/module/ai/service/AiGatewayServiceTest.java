package com.personal.assistant.module.ai.service;

import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.module.ai.dto.AiAssistRequest;
import com.personal.assistant.module.ai.entity.AiInvocation;
import com.personal.assistant.module.ai.mapper.AiInvocationMapper;
import com.personal.assistant.module.ai.mapper.AiProviderConfigMapper;
import com.personal.assistant.module.reminder.service.SecretCryptoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiGatewayServiceTest {
    @Mock
    private AiProviderConfigMapper configs;
    @Mock
    private AiInvocationMapper invocations;
    @Mock
    private SecretCryptoService crypto;
    @Mock
    private JdbcTemplate jdbc;

    @Test
    void unavailableProviderDoesNotBlockAndInputIsRedacted() {
        when(configs.selectOne(any())).thenReturn(null);
        AiGatewayService service = service();

        AiInvocation result = service.assist(
                2L, new AiAssistRequest("CONTENT_TAGGING", null, null, "联系 13812345678，邮箱 a@example.com"));

        assertEquals("FAILED", result.getStatus());
        assertFalse(result.getRedactedInput().contains("13812345678"));
        assertTrue(result.getRedactedInput().contains("[邮箱]"));
        verify(invocations).insert(result);
        verify(invocations).updateById(result);
    }

    @Test
    void rejectsSourceOwnedByAnotherUserBeforeCreatingInvocation() {
        when(jdbc.queryForObject(
                eq("select count(*) from knowledge_entry where id=? and user_id=?"),
                eq(Integer.class), eq(9L), eq(2L))).thenReturn(0);
        AiGatewayService service = service();

        assertThrows(BusinessException.class, () -> service.assist(
                2L, new AiAssistRequest("CONTENT_TAGGING", "KNOWLEDGE", 9L, "测试")));
    }

    private AiGatewayService service() {
        return new AiGatewayService(configs, invocations, crypto, jdbc, List.of());
    }
}
