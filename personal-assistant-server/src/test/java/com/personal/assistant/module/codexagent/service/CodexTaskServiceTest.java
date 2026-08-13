package com.personal.assistant.module.codexagent.service;

import com.personal.assistant.module.codexagent.dto.CodexAgentDtos.LeaseRequest;
import com.personal.assistant.module.codexagent.entity.CodexTask;
import com.personal.assistant.module.codexagent.mapper.CodexAgentMapper;
import com.personal.assistant.module.codexagent.mapper.CodexTaskEventMapper;
import com.personal.assistant.module.codexagent.mapper.CodexTaskMapper;
import com.personal.assistant.module.wecom.WeComMessageService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodexTaskServiceTest {
    private final CodexTaskMapper tasks = mock(CodexTaskMapper.class);
    private final CodexTaskService service = new CodexTaskService(tasks, mock(CodexTaskEventMapper.class),
            mock(CodexAgentMapper.class), mock(CodexAgentService.class), mock(WeComMessageService.class));

    @Test
    void cancelRunningTaskRequestsAgentTermination() {
        CodexTask task = runningTask();
        when(tasks.selectById(9L)).thenReturn(task);

        service.cancel(3L, 9L);

        assertEquals("CANCEL_REQUESTED", task.getStatus());
        verify(tasks).updateById(task);
    }

    @Test
    void agentCanObserveAndConfirmCancellation() {
        CodexTask task = runningTask();
        task.setStatus("CANCEL_REQUESTED");
        when(tasks.selectById(9L)).thenReturn(task);

        assertTrue(service.control(7L, 9L, new LeaseRequest("lease-1")).cancelRequested());
        service.cancelled(7L, 9L, new LeaseRequest("lease-1"));

        assertEquals("CANCELLED", task.getStatus());
        verify(tasks).updateById(task);
    }

    private CodexTask runningTask() {
        CodexTask task = new CodexTask();
        task.setId(9L);
        task.setUserId(3L);
        task.setAgentId(7L);
        task.setStatus("RUNNING");
        task.setLeaseId("lease-1");
        return task;
    }
}
