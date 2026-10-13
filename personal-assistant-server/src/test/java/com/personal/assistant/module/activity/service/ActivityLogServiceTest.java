package com.personal.assistant.module.activity.service;

import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.module.activity.dto.ActivityLogCreateRequest;
import com.personal.assistant.module.activity.dto.ActivityLogUpdateRequest;
import com.personal.assistant.module.activity.entity.ActivityLog;
import com.personal.assistant.module.activity.mapper.ActivityLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityLogServiceTest {
    @Mock private ActivityLogMapper mapper;

    @Test
    void createFillsOwnerDefaultsAndDerivesLogDateFromOccurredAt() {
        ActivityLogService service = new ActivityLogService(mapper);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 10, 13, 9, 30);

        service.create(7L, new ActivityLogCreateRequest("  写完了活动记录模块  ", null, occurredAt, null, null, null));

        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(mapper).insert(captor.capture());
        ActivityLog saved = captor.getValue();
        assertEquals(7L, saved.getUserId());
        assertEquals("写完了活动记录模块", saved.getContent());
        assertEquals(LocalDate.of(2026, 10, 13), saved.getLogDate());
        assertEquals(occurredAt, saved.getOccurredAt());
        assertEquals("OTHER", saved.getCategory());
        assertEquals("WEB", saved.getSource());
        assertFalse(saved.getPinned());
        assertNull(saved.getTags());
    }

    @Test
    void createKeepsExplicitLogDateSoYesterdayCanBeBackfilled() {
        ActivityLogService service = new ActivityLogService(mapper);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 10, 13, 9, 30);

        service.create(7L, new ActivityLogCreateRequest("补记昨天的事", LocalDate.of(2026, 10, 12), occurredAt, "WORK", " 方案,方案 , 复盘 ", "mobile_web"));

        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(mapper).insert(captor.capture());
        ActivityLog saved = captor.getValue();
        assertEquals(LocalDate.of(2026, 10, 12), saved.getLogDate());
        assertEquals("WORK", saved.getCategory());
        assertEquals("方案,复盘", saved.getTags());
        assertEquals("MOBILE_WEB", saved.getSource());
    }

    @Test
    void createFallsBackToOtherForUnknownCategory() {
        ActivityLogService service = new ActivityLogService(mapper);

        service.create(7L, new ActivityLogCreateRequest("随手一记", null, null, "SOMETHING_ELSE", null, null));

        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(mapper).insert(captor.capture());
        assertEquals("OTHER", captor.getValue().getCategory());
        assertEquals(LocalDate.now(), captor.getValue().getLogDate());
    }

    @Test
    void togglePinFlipsCurrentValue() {
        ActivityLog log = new ActivityLog();
        log.setId(1L);
        log.setUserId(7L);
        log.setPinned(false);
        when(mapper.selectById(1L)).thenReturn(log);

        ActivityLogService service = new ActivityLogService(mapper);
        assertTrue(service.togglePin(7L, 1L).pinned());

        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(mapper).updateById(captor.capture());
        assertTrue(captor.getValue().getPinned());
    }

    @Test
    void updateRejectsAnotherUsersRecord() {
        ActivityLog log = new ActivityLog();
        log.setId(1L);
        log.setUserId(8L);
        when(mapper.selectById(1L)).thenReturn(log);

        ActivityLogService service = new ActivityLogService(mapper);
        ActivityLogUpdateRequest request = new ActivityLogUpdateRequest("改一下", null, null, null, null, null);
        assertThrows(BusinessException.class, () -> service.update(7L, 1L, request));
    }

    @Test
    void deleteRejectsMissingRecord() {
        when(mapper.selectById(99L)).thenReturn(null);

        ActivityLogService service = new ActivityLogService(mapper);
        assertThrows(BusinessException.class, () -> service.delete(7L, 99L));
    }

    @Test
    void calendarRejectsReversedRange() {
        ActivityLogService service = new ActivityLogService(mapper);
        assertThrows(BusinessException.class,
                () -> service.calendar(7L, LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 1)));
    }
}
