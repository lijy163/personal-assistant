package com.personal.assistant.module.codexagent.service;

import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.module.codexagent.entity.CodexAgent;
import com.personal.assistant.module.codexagent.mapper.CodexAgentMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class CodexAgentModelServiceTest {
    @Test
    void updatesModelAndReasoningEffort() {
        CodexAgentMapper mapper = mock(CodexAgentMapper.class);
        CodexAgent agent = new CodexAgent();
        agent.setId(2L);
        agent.setUserId(1L);
        when(mapper.selectById(2L)).thenReturn(agent);
        CodexAgentService service = new CodexAgentService(mapper);

        service.updateModel(1L, 2L, "gpt-5.6-sol", "high");

        assertEquals("gpt-5.6-sol", agent.getModel());
        assertEquals("high", agent.getReasoningEffort());
        verify(mapper).updateById(agent);
    }

    @Test
    void rejectsUnsafeModelAndUnknownEffort() {
        CodexAgentMapper mapper = mock(CodexAgentMapper.class);
        CodexAgent agent = new CodexAgent();
        agent.setId(2L);
        agent.setUserId(1L);
        when(mapper.selectById(2L)).thenReturn(agent);
        CodexAgentService service = new CodexAgentService(mapper);

        assertThrows(BusinessException.class, () -> service.updateModel(1L, 2L, "model --flag", "high"));
        assertThrows(BusinessException.class, () -> service.updateModel(1L, 2L, "gpt-5.6-sol", "ultra"));
    }
}
