package com.personal.assistant.module.wecom;

import com.personal.assistant.module.codexagent.entity.CodexTask;
import com.personal.assistant.module.codexagent.entity.CodexAgent;
import com.personal.assistant.module.codexagent.mapper.CodexTaskMapper;
import com.personal.assistant.module.codexagent.service.CodexAgentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class WeComCommandServiceTest {
    @Test
    void createsReadOnlyTaskAndRequiresConfirmationForWrite() {
        WeComProperties properties = properties();
        CodexTaskMapper tasks = mock(CodexTaskMapper.class);
        CodexAgentService agents = mock(CodexAgentService.class);
        WeComMessageService messages = mock(WeComMessageService.class);
        when(tasks.selectCount(any())).thenReturn(0L);
        CodexAgent agent = new CodexAgent();
        agent.setUserId(1L);
        when(agents.requireActive(2L)).thenReturn(agent);
        doAnswer(invocation -> { invocation.<CodexTask>getArgument(0).setId(10L); return 1; })
                .when(tasks).insert(any(CodexTask.class));
        WeComCommandService service = new WeComCommandService(properties, tasks, agents, messages);

        service.handle(new WeComCryptoService.IncomingMessage("user1", "text", "问 personal-assistant 分析问题", "m1"));
        service.handle(new WeComCryptoService.IncomingMessage("user1", "text", "改 personal-assistant 修复问题", "m2"));

        ArgumentCaptor<CodexTask> captor = ArgumentCaptor.forClass(CodexTask.class);
        verify(tasks, times(2)).insert(captor.capture());
        assertEquals("PENDING", captor.getAllValues().get(0).getStatus());
        assertEquals("READ_ONLY", captor.getAllValues().get(0).getPermissionMode());
        assertEquals("WAITING_CONFIRMATION", captor.getAllValues().get(1).getStatus());
        assertEquals("WORKSPACE_WRITE", captor.getAllValues().get(1).getPermissionMode());
        verify(messages).sendText(eq("user1"), contains("确认 10"));
    }

    private WeComProperties properties() {
        WeComProperties properties = new WeComProperties();
        properties.setDefaultCodexAgentId(2L);
        properties.setAllowedUsers(Set.of("user1"));
        return properties;
    }
}
