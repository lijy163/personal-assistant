package com.personal.assistant.module.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.module.reminder.entity.NotificationChannel;
import com.personal.assistant.module.reminder.mapper.NotificationChannelMapper;
import com.personal.assistant.module.reminder.service.NotificationService;
import com.personal.assistant.module.task.entity.TaskItem;
import com.personal.assistant.module.task.mapper.TaskItemMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 待办推送。
 *
 * <p>每日摘要与到点提醒是两套口径，互不依赖：
 * 摘要回答「今天该做什么」，覆盖生活、工作、学习三类，不看 reminderEnabled；
 * 到点提醒回答「现在提醒我」，只针对配了 reminderAt 的生活事项。</p>
 */
@Service
public class WorkdayTaskReminderService {
    private static final Logger log = LoggerFactory.getLogger(WorkdayTaskReminderService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int LOOKAHEAD_DAYS = 7;
    private static final List<String> OPEN_STATUSES = List.of("NOT_STARTED", "IN_PROGRESS");
    private static final Set<String> DIRECT_CHANNELS = Set.of("SERVER_CHAN", "PUSHPLUS");

    private final TaskItemMapper tasks;
    private final NotificationChannelMapper channels;
    private final NotificationService notifications;

    public WorkdayTaskReminderService(TaskItemMapper tasks, NotificationChannelMapper channels,
                                      NotificationService notifications) {
        this.tasks = tasks;
        this.channels = channels;
        this.notifications = notifications;
    }

    @Scheduled(cron = "0 30 8 * * *", zone = "Asia/Shanghai")
    public void sendWorkdaySummary() {
        sendSummaryFor(LocalDate.now(ZONE));
    }

    void sendSummaryFor(LocalDate today) {
        LocalDate horizon = today.plusDays(LOOKAHEAD_DAYS);
        List<TaskItem> pending = tasks.selectList(new LambdaQueryWrapper<TaskItem>()
                .eq(TaskItem::getArchived, false)
                .in(TaskItem::getStatus, OPEN_STATUSES));
        Map<Long, List<TaskItem>> byUser = pending.stream()
                .filter(item -> withinSummaryWindow(item, horizon))
                .sorted(Comparator
                        .comparing(this::effectiveDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TaskItem::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.groupingBy(TaskItem::getUserId, LinkedHashMap::new, Collectors.toList()));
        byUser.forEach((userId, items) -> sendUserSummarySafely(userId, items, today));
    }

    @Scheduled(fixedDelay = 60000)
    public void sendDueLifeReminders() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        tasks.selectList(new LambdaQueryWrapper<TaskItem>()
                        .eq(TaskItem::getItemType, "LIFE")
                        .eq(TaskItem::getArchived, false)
                        .eq(TaskItem::getReminderEnabled, true)
                        .in(TaskItem::getStatus, "DRAFT", "NOT_STARTED", "IN_PROGRESS")
                        .isNotNull(TaskItem::getReminderAt)
                        .isNull(TaskItem::getReminderSentAt)
                        .le(TaskItem::getReminderAt, now)
                        .orderByAsc(TaskItem::getReminderAt)
                        .last("limit 200"))
                .forEach(item -> sendLifeReminderSafely(item, now));
    }

    /**
     * 摘要只收「逾期、今天、未来 7 天」以及没有排期但正在做的事项。
     * 没有时间锚点又没开始的待办不推，避免积压事项每天刷屏。
     */
    private boolean withinSummaryWindow(TaskItem item, LocalDate horizon) {
        LocalDate date = effectiveDate(item);
        if (date == null) return "IN_PROGRESS".equals(item.getStatus());
        return !date.isAfter(horizon);
    }

    private LocalDate effectiveDate(TaskItem item) {
        if (item.getDeadline() != null) return item.getDeadline().toLocalDate();
        if (item.getPlanTime() != null) return item.getPlanTime().toLocalDate();
        return null;
    }

    /**
     * 输出 Markdown：Server酱 的 desp 和 PushPlus 的 markdown 模板都会渲染它，
     * 微信里点开是有分节的排版而不是一坨纯文本。
     * 不渲染 Markdown 的渠道由 NotificationService 降级成纯文本。
     */
    private String buildContent(List<TaskItem> items, LocalDate today) {
        LocalDate horizon = today.plusDays(LOOKAHEAD_DAYS);
        List<Section> sections = List.of(
                new Section("逾期", items.stream().filter(item -> isOverdue(item, today)).toList()),
                new Section("今天", items.stream().filter(item -> today.equals(effectiveDate(item))).toList()),
                new Section("未来 " + LOOKAHEAD_DAYS + " 天", items.stream().filter(item -> {
                    LocalDate date = effectiveDate(item);
                    return date != null && date.isAfter(today) && !date.isAfter(horizon);
                }).toList()),
                new Section("进行中", items.stream().filter(item -> effectiveDate(item) == null).toList()));

        StringBuilder text = new StringBuilder();
        int index = 0;
        for (Section section : sections) {
            if (section.items().isEmpty()) continue;
            if (text.length() > 0) text.append('\n');
            text.append("### ").append(section.heading()).append('\n');
            for (TaskItem item : section.items()) {
                index++;
                text.append(index).append(". ").append(item.getTitle());
                long days = overdueDays(item, today);
                if (days > 0) text.append(" · **逾期 ").append(days).append(" 天**");
                text.append('\n');
            }
        }
        return text.toString().strip();
    }

    private boolean isOverdue(TaskItem item, LocalDate today) {
        LocalDate date = effectiveDate(item);
        return date != null && date.isBefore(today);
    }

    private long overdueDays(TaskItem item, LocalDate today) {
        LocalDate date = effectiveDate(item);
        return date == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(date, today));
    }

    private void sendLifeReminderSafely(TaskItem item, LocalDateTime sentAt) {
        try {
            NotificationChannel channel = preferredChannel(item.getUserId());
            if (channel == null) return;
            notifications.send(item.getUserId(), null, channel.getId(), "生活事项提醒", item.getTitle());
            item.setReminderSentAt(sentAt);
            item.setUpdatedAt(sentAt);
            tasks.updateById(item);
        } catch (RuntimeException exception) {
            log.warn("生活事项提醒发送失败，taskId={}", item.getId(), exception);
        }
    }

    private void sendUserSummarySafely(Long userId, List<TaskItem> items, LocalDate today) {
        try {
            NotificationChannel channel = preferredChannel(userId);
            if (channel == null) {
                log.debug("用户 {} 没有启用的通知通道，跳过待办摘要", userId);
                return;
            }
            long overdue = items.stream().filter(item -> isOverdue(item, today)).count();
            String title = "近期待办 " + items.size() + " 项" + (overdue > 0 ? " · 逾期 " + overdue : "");
            notifications.send(userId, null, channel.getId(), title, buildContent(items, today));
        } catch (RuntimeException exception) {
            log.warn("待办摘要发送失败，userId={}", userId, exception);
        }
    }

    private NotificationChannel preferredChannel(Long userId) {
        return channels.selectList(new LambdaQueryWrapper<NotificationChannel>()
                        .eq(NotificationChannel::getUserId, userId)
                        .eq(NotificationChannel::getEnabled, true))
                .stream()
                // 能直接推到微信的通道优先；同为直推或同为通用 webhook 时，先配的优先。
                .min(Comparator.comparingInt((NotificationChannel channel) ->
                                DIRECT_CHANNELS.contains(channel.getChannelType()) ? 0 : 1)
                        .thenComparing(NotificationChannel::getId))
                .orElse(null);
    }

    private record Section(String heading, List<TaskItem> items) {
    }
}
