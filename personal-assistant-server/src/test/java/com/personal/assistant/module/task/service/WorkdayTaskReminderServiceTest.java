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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkdayTaskReminderServiceTest {
    @Mock TaskItemMapper tasks;
    @Mock NotificationChannelMapper channels;
    @Mock NotificationService notifications;
    WorkdayTaskReminderService service;

    @BeforeEach
    void setUp() {
        service = new WorkdayTaskReminderService(tasks, channels, notifications);
    }

    @Test
    void combinesTitlesIntoOneServerChanMessage() {
        when(tasks.selectList(any())).thenReturn(List.of(task(7L, "修复登录"), task(7L, "发布版本")));
        when(channels.selectList(any())).thenReturn(List.of(channel(4L, "WEBHOOK"), channel(3L, "SERVER_CHAN")));

        service.sendWorkdaySummary();

        verify(notifications).send(7L, null, 3L, "工作事项提醒（2项）", "1. 修复登录\n2. 发布版本");
    }

    @Test
    void skipsUserWithoutEnabledChannel() {
        when(tasks.selectList(any())).thenReturn(List.of(task(7L, "修复登录")));
        when(channels.selectList(any())).thenReturn(List.of());

        service.sendWorkdaySummary();

        verify(notifications, never()).send(any(), any(), any(), any(), any());
    }

    private TaskItem task(Long userId, String title) {
        TaskItem task = new TaskItem();
        task.setUserId(userId);
        task.setTitle(title);
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
