package com.personal.assistant.module.checkin.service;

import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.module.activity.dto.ActivityLogCreateRequest;
import com.personal.assistant.module.activity.dto.ActivityLogResponse;
import com.personal.assistant.module.activity.service.ActivityLogService;
import com.personal.assistant.module.checkin.dto.CheckinOverview;
import com.personal.assistant.module.checkin.dto.CheckinResult;
import com.personal.assistant.module.checkin.entity.Habit;
import com.personal.assistant.module.checkin.entity.HabitCheckin;
import com.personal.assistant.module.checkin.mapper.HabitCheckinMapper;
import com.personal.assistant.module.checkin.mapper.HabitMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckinServiceTest {

    @Mock private HabitMapper habitMapper;
    @Mock private HabitCheckinMapper habitCheckinMapper;
    @Mock private ActivityLogService activityLogService;

    private CheckinService service() {
        return new CheckinService(habitMapper, habitCheckinMapper, activityLogService);
    }

    @Test
    void checkWritesActivityLogAndLinksItToTheCheckin() {
        LocalDate today = LocalDate.now();
        when(habitMapper.selectById(1L)).thenReturn(habit(1L, 7L, "早起", true));
        when(habitCheckinMapper.selectOne(any())).thenReturn(null);
        when(habitCheckinMapper.selectList(any())).thenReturn(List.of());
        when(activityLogService.create(eq(7L), any())).thenReturn(activityLog(88L, today));

        CheckinResult result = service().check(7L, 1L, today, "  7 点前起床  ");

        assertTrue(result.checked());
        assertEquals(88L, result.activityLogId());
        assertEquals(today, result.checkinDate());

        ArgumentCaptor<HabitCheckin> checkin = ArgumentCaptor.forClass(HabitCheckin.class);
        verify(habitCheckinMapper).insert(checkin.capture());
        assertEquals(today, checkin.getValue().getCheckinDate());
        assertEquals("7 点前起床", checkin.getValue().getNote());
        assertEquals("CHECKIN", checkin.getValue().getSource());
        verify(habitCheckinMapper).updateById(checkin.getValue());

        ArgumentCaptor<ActivityLogCreateRequest> request = ArgumentCaptor.forClass(ActivityLogCreateRequest.class);
        verify(activityLogService).create(eq(7L), request.capture());
        assertEquals("打卡：早起 · 7 点前起床", request.getValue().content());
        assertEquals(today, request.getValue().logDate());
        assertEquals("REST", request.getValue().category());
        assertEquals("打卡,早起", request.getValue().tags());
        assertEquals("CHECKIN", request.getValue().source());
    }

    @Test
    void checkIsIdempotentForTheSameDay() {
        LocalDate today = LocalDate.now();
        HabitCheckin existing = new HabitCheckin();
        existing.setId(5L);
        existing.setHabitId(1L);
        existing.setUserId(7L);
        existing.setCheckinDate(today);
        existing.setActivityLogId(88L);

        when(habitMapper.selectById(1L)).thenReturn(habit(1L, 7L, "早起", true));
        when(habitCheckinMapper.selectOne(any())).thenReturn(existing);
        when(habitCheckinMapper.selectList(any())).thenReturn(List.of(existing));

        CheckinResult result = service().check(7L, 1L, today, null);

        assertTrue(result.checked());
        assertEquals(88L, result.activityLogId());
        assertEquals(1, result.streak());
        verify(habitCheckinMapper, never()).insert(any(HabitCheckin.class));
        verify(activityLogService, never()).create(any(), any());
    }

    @Test
    void cancelDeletesTheSyncedActivityLog() {
        LocalDate today = LocalDate.now();
        HabitCheckin existing = new HabitCheckin();
        existing.setId(5L);
        existing.setHabitId(1L);
        existing.setUserId(7L);
        existing.setCheckinDate(today);
        existing.setActivityLogId(88L);

        when(habitMapper.selectById(1L)).thenReturn(habit(1L, 7L, "早起", true));
        when(habitCheckinMapper.selectOne(any())).thenReturn(existing);
        when(habitCheckinMapper.selectList(any())).thenReturn(List.of());

        CheckinResult result = service().cancel(7L, 1L, today);

        assertFalse(result.checked());
        verify(activityLogService).deleteIfExists(7L, 88L);
        verify(habitCheckinMapper).deleteById(5L);
    }

    @Test
    void checkRejectsFutureDate() {
        when(habitMapper.selectById(1L)).thenReturn(habit(1L, 7L, "早起", true));

        assertThrows(BusinessException.class,
                () -> service().check(7L, 1L, LocalDate.now().plusDays(1), null));
        verify(habitCheckinMapper, never()).insert(any(HabitCheckin.class));
    }

    @Test
    void checkRejectsInactiveHabit() {
        when(habitMapper.selectById(1L)).thenReturn(habit(1L, 7L, "早起", false));

        assertThrows(BusinessException.class, () -> service().check(7L, 1L, LocalDate.now(), null));
    }

    @Test
    void overviewSummarisesStreakAndTodayRecords() {
        LocalDate today = LocalDate.now();
        when(habitMapper.selectList(any())).thenReturn(List.of(habit(1L, 7L, "早起", true)));
        when(habitCheckinMapper.selectList(any())).thenReturn(List.of(
                checkin(1L, today.minusDays(3)),
                checkin(2L, today.minusDays(1)),
                checkin(3L, today)));
        when(habitCheckinMapper.countDistinctDays(7L)).thenReturn(3L);

        CheckinOverview overview = service().overview(7L, today);

        assertEquals(1, overview.habitCount());
        assertEquals(1, overview.doneCount());
        assertEquals(2, overview.currentStreak());
        assertEquals(2, overview.habits().get(0).streak());
        long monthCount = List.of(today.minusDays(3), today.minusDays(1), today).stream()
                .filter(date -> !date.isBefore(today.withDayOfMonth(1)))
                .count();
        assertEquals(monthCount, overview.habits().get(0).monthCount());
        assertEquals(1, overview.records().size());
        assertEquals(3L, overview.totalDays());
    }

    private Habit habit(Long id, Long userId, String name, boolean active) {
        Habit habit = new Habit();
        habit.setId(id);
        habit.setUserId(userId);
        habit.setName(name);
        habit.setIcon("☀️");
        habit.setColor("#2563eb");
        habit.setCategory("REST");
        habit.setActive(active);
        habit.setSortOrder(0);
        return habit;
    }

    private HabitCheckin checkin(Long id, LocalDate date) {
        HabitCheckin checkin = new HabitCheckin();
        checkin.setId(id);
        checkin.setUserId(7L);
        checkin.setHabitId(1L);
        checkin.setCheckinDate(date);
        checkin.setSource("CHECKIN");
        checkin.setCreatedAt(LocalDateTime.now());
        return checkin;
    }

    private ActivityLogResponse activityLog(Long id, LocalDate date) {
        return new ActivityLogResponse(id, "打卡：早起", date, LocalDateTime.now(),
                "REST", "打卡,早起", "CHECKIN", false, LocalDateTime.now(), LocalDateTime.now());
    }
}
