package com.personal.assistant.module.task.service;

import com.personal.assistant.module.reminder.entity.NotificationChannel;
import com.personal.assistant.module.reminder.mapper.NotificationChannelMapper;
import com.personal.assistant.module.reminder.service.NotificationService;
import com.personal.assistant.module.task.entity.TaskItem;
import com.personal.assistant.module.task.mapper.TaskItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkdayTaskReminderServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);
    private static final long USER_ID = 7L;
    private static final long SERVER_CHAN_ID = 3L;

    @Mock TaskItemMapper tasks;
    @Mock NotificationChannelMapper channels;
    @Mock NotificationService notifications;
    WorkdayTaskReminderService service;

    @BeforeEach
    void setUp() {
        service = new WorkdayTaskReminderService(tasks, channels, notifications);
    }

    @Test
    void groupsSummaryIntoOverdueTodayUpcomingAndInProgress() {
        when(tasks.selectList(any())).thenReturn(List.of(
                task("交周报", "WORK", "NOT_STARTED", TODAY.minusDays(3), null),
                task("报销单", "WORK", "NOT_STARTED", TODAY.minusDays(1), null),
                task("开会", "WORK", "IN_PROGRESS", TODAY, null),
                task("体检", "LIFE", "NOT_STARTED", TODAY.plusDays(3), null),
                task("学英语", "LEARNING", "IN_PROGRESS", null, null)));
        when(channels.selectList(any())).thenReturn(List.of(channel(SERVER_CHAN_ID, "SERVER_CHAN")));

        service.sendSummaryFor(TODAY);

        verify(notifications).send(USER_ID, null, SERVER_CHAN_ID, "近期待办 5 项 · 逾期 2",
                "### 逾期\n"
                        + "1. 交周报 · **逾期 3 天**\n"
                        + "2. 报销单 · **逾期 1 天**\n"
                        + "\n"
                        + "### 今天\n"
                        + "3. 开会\n"
                        + "\n"
                        + "### 未来 7 天\n"
                        + "4. 体检\n"
                        + "\n"
                        + "### 进行中\n"
                        + "5. 学英语");
    }

    @Test
    void includesLifeAndLearningItemsWithoutReminderEnabled() {
        TaskItem life = task("缴水费", "LIFE", "NOT_STARTED", TODAY, null);
        life.setReminderEnabled(false);
        TaskItem work = task("写周报", "WORK", "NOT_STARTED", TODAY, null);
        work.setReminderEnabled(false);
        when(tasks.selectList(any())).thenReturn(List.of(life, work));
        when(channels.selectList(any())).thenReturn(List.of(channel(SERVER_CHAN_ID, "SERVER_CHAN")));

        service.sendSummaryFor(TODAY);

        verify(notifications).send(USER_ID, null, SERVER_CHAN_ID, "近期待办 2 项",
                "### 今天\n1. 缴水费\n2. 写周报");
    }

    @Test
    void excludesItemsBeyondLookaheadWindow() {
        when(tasks.selectList(any())).thenReturn(List.of(
                task("季度复盘", "WORK", "NOT_STARTED", TODAY.plusDays(30), null),
                task("体检", "LIFE", "NOT_STARTED", TODAY.plusDays(7), null)));
        when(channels.selectList(any())).thenReturn(List.of(channel(SERVER_CHAN_ID, "SERVER_CHAN")));

        service.sendSummaryFor(TODAY);

        verify(notifications).send(USER_ID, null, SERVER_CHAN_ID, "近期待办 1 项",
                "### 未来 7 天\n1. 体检");
    }

    @Test
    void excludesUndatedItemsThatHaveNotStarted() {
        when(tasks.selectList(any())).thenReturn(List.of(
                task("某个想法", "LIFE", "NOT_STARTED", null, null)));

        service.sendSummaryFor(TODAY);

        verify(notifications, never()).send(any(), any(), any(), any(), any());
    }

    @Test
    void fallsBackToPlanTimeWhenDeadlineMissing() {
        when(tasks.selectList(any())).thenReturn(List.of(
                task("健身", "LIFE", "NOT_STARTED", null, TODAY.atTime(19, 0))));
        when(channels.selectList(any())).thenReturn(List.of(channel(SERVER_CHAN_ID, "SERVER_CHAN")));

        service.sendSummaryFor(TODAY);

        verify(notifications).send(USER_ID, null, SERVER_CHAN_ID, "近期待办 1 项",
                "### 今天\n1. 健身");
    }

    @Test
    void prefersServerChanOverPlainWebhook() {
        when(tasks.selectList(any())).thenReturn(List.of(
                task("开会", "WORK", "NOT_STARTED", TODAY, null)));
        when(channels.selectList(any())).thenReturn(List.of(
                channel(4L, "WEBHOOK"), channel(SERVER_CHAN_ID, "SERVER_CHAN")));

        service.sendSummaryFor(TODAY);

        verify(notifications).send(USER_ID, null, SERVER_CHAN_ID, "近期待办 1 项", "### 今天\n1. 开会");
    }

    @Test
    void prefersPushPlusOverPlainWebhook() {
        when(tasks.selectList(any())).thenReturn(List.of(
                task("开会", "WORK", "NOT_STARTED", TODAY, null)));
        when(channels.selectList(any())).thenReturn(List.of(
                channel(4L, "WEBHOOK"), channel(9L, "PUSHPLUS")));

        service.sendSummaryFor(TODAY);

        verify(notifications).send(USER_ID, null, 9L, "近期待办 1 项", "### 今天\n1. 开会");
    }

    @Test
    void skipsUserWithoutEnabledChannel() {
        when(tasks.selectList(any())).thenReturn(List.of(
                task("修复登录", "WORK", "NOT_STARTED", TODAY, null)));
        when(channels.selectList(any())).thenReturn(List.of());

        service.sendSummaryFor(TODAY);

        verify(notifications, never()).send(any(), any(), any(), any(), any());
    }

    @Test
    void sendsDueLifeReminderOnceAndMarksItSent() {
        TaskItem item = task("缴水费", "LIFE", "NOT_STARTED", null, null);
        item.setId(12L);
        item.setReminderEnabled(true);
        item.setReminderAt(LocalDateTime.now().minusMinutes(1));
        when(tasks.selectList(any())).thenReturn(List.of(item));
        when(channels.selectList(any())).thenReturn(List.of(channel(SERVER_CHAN_ID, "SERVER_CHAN")));

        service.sendDueLifeReminders();

        verify(notifications).send(USER_ID, null, SERVER_CHAN_ID, "生活事项提醒", "缴水费");
        verify(tasks).updateById(item);
        org.junit.jupiter.api.Assertions.assertNotNull(item.getReminderSentAt());
    }

    private TaskItem task(String title, String itemType, String status, LocalDate deadline, LocalDateTime planTime) {
        TaskItem task = new TaskItem();
        task.setUserId(USER_ID);
        task.setTitle(title);
        task.setItemType(itemType);
        task.setStatus(status);
        task.setDeadline(deadline == null ? null : deadline.atTime(18, 0));
        task.setPlanTime(planTime);
        task.setArchived(false);
        task.setReminderEnabled(false);
        return task;
    }

    private NotificationChannel channel(Long id, String type) {
        NotificationChannel channel = new NotificationChannel();
        channel.setId(id);
        channel.setChannelType(type);
        channel.setEnabled(true);
        return channel;
    }
}
