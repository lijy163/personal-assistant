package com.personal.assistant.module.devlog.service;

import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.module.devlog.dto.DevLogIngestRequest;
import com.personal.assistant.module.devlog.entity.DevLog;
import com.personal.assistant.module.devlog.mapper.DevLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevLogServiceTest {
    @Mock DevLogMapper mapper;

    @Test
    void ingestCreatesStructuredLog() {
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.insert(any(DevLog.class))).thenAnswer(invocation -> {
            DevLog log = invocation.getArgument(0);
            log.setId(12L);
            return 1;
        });
        DevLogService service = new DevLogService(mapper);
        Long id = service.ingest(7L, request());
        ArgumentCaptor<DevLog> captor = ArgumentCaptor.forClass(DevLog.class);
        verify(mapper).insert(captor.capture());
        assertEquals(12L, id);
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals("CODEX", captor.getValue().getSource());
    }

    @Test
    void ingestReturnsExistingIdForSameFingerprint() {
        DevLog existing = new DevLog(); existing.setId(9L);
        when(mapper.selectOne(any())).thenReturn(existing);
        assertEquals(9L, new DevLogService(mapper).ingest(7L, request()));
    }

    @Test
    void detailRejectsAnotherUsersLog() {
        DevLog log = new DevLog(); log.setUserId(8L);
        when(mapper.selectById(1L)).thenReturn(log);
        assertThrows(BusinessException.class, () -> new DevLogService(mapper).detail(7L, 1L));
    }

    private DevLogIngestRequest request() {
        return new DevLogIngestRequest("abc", "标题", "项目", null, null, null, "目标", "修改",
                null, null, "通过", "test", null, null, "# 标题");
    }
}
