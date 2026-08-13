package com.personal.assistant.module.publiccodex;

import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.module.codexagent.entity.CodexAgent;
import com.personal.assistant.module.codexagent.entity.CodexTask;
import com.personal.assistant.module.codexagent.mapper.CodexTaskMapper;
import com.personal.assistant.module.codexagent.service.CodexAgentService;
import com.personal.assistant.module.codexagent.service.CodexCloudConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PublicCodexServiceTest {
    private static final String SESSION = "a".repeat(64);

    @Test
    void createsOnlyReadOnlyTaskInConfiguredPublicProject() {
        PublicCodexProperties properties = properties();
        CodexAgentService agents = mock(CodexAgentService.class);
        CodexTaskMapper tasks = mock(CodexTaskMapper.class);
        CodexAgent agent = new CodexAgent();
        agent.setId(8L);
        agent.setUserId(3L);
        when(agents.requireActive(8L)).thenReturn(agent);
        when(tasks.selectCount(any())).thenReturn(0L, 0L);
        doAnswer(invocation -> { invocation.<CodexTask>getArgument(0).setId(12L); return 1; })
                .when(tasks).insert(any(CodexTask.class));

        PublicCodexService service = new PublicCodexService(properties, agents, tasks, cloudConfig(false, null));
        service.ask(SESSION, "什么是单元测试？");

        ArgumentCaptor<CodexTask> captor = ArgumentCaptor.forClass(CodexTask.class);
        verify(tasks).insert(captor.capture());
        CodexTask task = captor.getValue();
        assertEquals("READ_ONLY", task.getPermissionMode());
        assertEquals("public-qa", task.getProjectKey());
        assertEquals("PUBLIC", task.getSource());
        assertEquals(8L, task.getAgentId());
    }

    @Test
    void rejectsAnswerLookupFromAnotherSession() {
        CodexTaskMapper tasks = mock(CodexTaskMapper.class);
        CodexTask task = new CodexTask();
        task.setId(12L);
        task.setSource("PUBLIC");
        task.setExternalUserId("not-this-session");
        when(tasks.selectById(12L)).thenReturn(task);
        PublicCodexService service = new PublicCodexService(properties(), mock(CodexAgentService.class), tasks,
                cloudConfig(false, null));

        assertThrows(BusinessException.class, () -> service.answer(SESSION, 12L));
    }

    private PublicCodexProperties properties() {
        PublicCodexProperties properties = new PublicCodexProperties();
        properties.setEnabled(true);
        properties.setAgentId(8L);
        properties.setProjectKey("public-qa");
        return properties;
    }

    private CodexCloudConfigService cloudConfig(boolean enabled, Long agentId) {
        CodexCloudConfigService config = mock(CodexCloudConfigService.class);
        when(config.publicEnabled()).thenReturn(enabled);
        when(config.publicAgentId()).thenReturn(agentId);
        when(config.configured()).thenReturn(false);
        return config;
    }
}
