package com.personal.assistant.module.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.module.reminder.entity.NotificationChannel;
import com.personal.assistant.module.reminder.mapper.NotificationChannelMapper;
import com.personal.assistant.module.reminder.service.NotificationService;
import com.personal.assistant.module.task.entity.TaskItem;
import com.personal.assistant.module.task.mapper.TaskItemMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkdayTaskReminderService {
    private final TaskItemMapper tasks;
    private final NotificationChannelMapper channels;
    private final NotificationService notifications;

    public WorkdayTaskReminderService(TaskItemMapper tasks, NotificationChannelMapper channels,
                                      NotificationService notifications) {
        this.tasks = tasks;
        this.channels = channels;
        this.notifications = notifications;
    }

    @Scheduled(cron = "0 50 8 * * MON-FRI", zone = "Asia/Shanghai")
    public void sendWorkdaySummary() {
        List<TaskItem> pending = tasks.selectList(new LambdaQueryWrapper<TaskItem>()
                .eq(TaskItem::getItemType, "WORK")
                .eq(TaskItem::getArchived, false)
                .eq(TaskItem::getReminderEnabled, true)
                .in(TaskItem::getStatus, "NOT_STARTED", "IN_PROGRESS")
                .orderByAsc(TaskItem::getDeadline)
                .orderByAsc(TaskItem::getCreatedAt));
        Map<Long, List<TaskItem>> byUser = pending.stream()
                .collect(java.util.stream.Collectors.groupingBy(TaskItem::getUserId, LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        byUser.forEach(this::sendUserSummarySafely);
    }

    private void sendUserSummarySafely(Long userId, List<TaskItem> items) {
        try {
            NotificationChannel channel = preferredChannel(userId);
            if (channel == null) return;
            String content = java.util.stream.IntStream.range(0, items.size())
                    .mapToObj(index -> (index + 1) + ". " + items.get(index).getTitle())
                    .collect(java.util.stream.Collectors.joining("\n"));
            notifications.send(userId, null, channel.getId(), "工作事项提醒（" + items.size() + "项）", content);
        } catch (RuntimeException ignored) {
        }
    }

    private NotificationChannel preferredChannel(Long userId) {
        return channels.selectList(new LambdaQueryWrapper<NotificationChannel>()
                        .eq(NotificationChannel::getUserId, userId)
                        .eq(NotificationChannel::getEnabled, true))
                .stream()
                .min(Comparator.comparing((NotificationChannel channel) ->
                                "SERVER_CHAN".equals(channel.getChannelType()) ? 0 : 1)
                        .thenComparing(NotificationChannel::getId))
                .orElse(null);
    }
}
