package com.personal.assistant.module.checkin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.activity.dto.ActivityLogCreateRequest;
import com.personal.assistant.module.activity.dto.ActivityLogResponse;
import com.personal.assistant.module.activity.service.ActivityLogService;
import com.personal.assistant.module.checkin.dto.CheckinDaySummary;
import com.personal.assistant.module.checkin.dto.CheckinHabitResponse;
import com.personal.assistant.module.checkin.dto.CheckinOverview;
import com.personal.assistant.module.checkin.dto.CheckinRecordResponse;
import com.personal.assistant.module.checkin.dto.CheckinResult;
import com.personal.assistant.module.checkin.dto.HabitRemovalResult;
import com.personal.assistant.module.checkin.dto.HabitRequest;
import com.personal.assistant.module.checkin.entity.Habit;
import com.personal.assistant.module.checkin.entity.HabitCheckin;
import com.personal.assistant.module.checkin.mapper.HabitCheckinMapper;
import com.personal.assistant.module.checkin.mapper.HabitMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 打卡服务。打卡项与打卡记录沿用生活管理的 habit / habit_checkin 表，
 * 每打一次卡都在活动记录里写一条来源为 CHECKIN 的记录，撤销打卡时同步回滚。
 */
@Service
public class CheckinService {

    private static final Set<String> CATEGORIES =
            Set.of("WORK", "STUDY", "EXERCISE", "FOOD", "SOCIAL", "REST", "OTHER");
    private static final Pattern COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final String DEFAULT_COLOR = "#2563eb";
    private static final String SOURCE_CHECKIN = "CHECKIN";
    private static final String TAG_CHECKIN = "打卡";
    private static final int MAX_ICON_LENGTH = 16;
    private static final int MAX_NOTE_LENGTH = 200;
    private static final int MAX_TAG_LENGTH = 30;
    private static final int HISTORY_DAYS = 400;
    private static final int DEFAULT_CALENDAR_DAYS = 365;

    private final HabitMapper habitMapper;
    private final HabitCheckinMapper habitCheckinMapper;
    private final ActivityLogService activityLogService;

    public CheckinService(HabitMapper habitMapper,
                          HabitCheckinMapper habitCheckinMapper,
                          ActivityLogService activityLogService) {
        this.habitMapper = habitMapper;
        this.habitCheckinMapper = habitCheckinMapper;
        this.activityLogService = activityLogService;
    }

    public CheckinOverview overview(Long userId, LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        List<Habit> habits = habitMapper.selectList(new LambdaQueryWrapper<Habit>()
                .eq(Habit::getUserId, userId)
                .orderByAsc(Habit::getSortOrder)
                .orderByAsc(Habit::getId));

        List<HabitCheckin> recent = habitCheckinMapper.selectList(new LambdaQueryWrapper<HabitCheckin>()
                .eq(HabitCheckin::getUserId, userId)
                .ge(HabitCheckin::getCheckinDate, target.minusDays(HISTORY_DAYS))
                .le(HabitCheckin::getCheckinDate, target)
                .orderByAsc(HabitCheckin::getCheckinDate)
                .orderByAsc(HabitCheckin::getId));

        Map<Long, List<HabitCheckin>> byHabit = recent.stream()
                .collect(Collectors.groupingBy(HabitCheckin::getHabitId));
        Map<Long, Habit> habitById = habits.stream()
                .collect(Collectors.toMap(Habit::getId, habit -> habit));
        LocalDate monthStart = target.withDayOfMonth(1);

        List<CheckinHabitResponse> responses = habits.stream().map(habit -> {
            List<HabitCheckin> items = byHabit.getOrDefault(habit.getId(), List.of());
            Set<LocalDate> dates = items.stream()
                    .map(HabitCheckin::getCheckinDate)
                    .collect(Collectors.toCollection(TreeSet::new));
            long monthCount = items.stream()
                    .filter(item -> !item.getCheckinDate().isBefore(monthStart))
                    .count();
            LocalDate last = items.isEmpty() ? null : items.get(items.size() - 1).getCheckinDate();
            return new CheckinHabitResponse(
                    habit.getId(),
                    habit.getName(),
                    habit.getIcon(),
                    habit.getColor(),
                    habit.getCategory(),
                    habit.getNote(),
                    habit.getActive(),
                    habit.getSortOrder(),
                    dates.contains(target),
                    streak(dates, target),
                    monthCount,
                    last);
        }).toList();

        int habitCount = (int) responses.stream()
                .filter(item -> Boolean.TRUE.equals(item.active()))
                .count();
        int doneCount = (int) responses.stream()
                .filter(item -> Boolean.TRUE.equals(item.active()) && Boolean.TRUE.equals(item.checked()))
                .count();
        Set<LocalDate> allDates = recent.stream()
                .map(HabitCheckin::getCheckinDate)
                .collect(Collectors.toCollection(TreeSet::new));
        long monthCount = recent.stream()
                .filter(item -> !item.getCheckinDate().isBefore(monthStart))
                .count();

        List<CheckinRecordResponse> records = recent.stream()
                .filter(item -> item.getCheckinDate().equals(target))
                .sorted(Comparator.comparing(HabitCheckin::getId).reversed())
                .map(item -> {
                    Habit habit = habitById.get(item.getHabitId());
                    return new CheckinRecordResponse(
                            item.getId(),
                            item.getHabitId(),
                            habit != null ? habit.getName() : "已移除的打卡项",
                            habit != null ? habit.getIcon() : null,
                            habit != null ? habit.getColor() : null,
                            item.getCheckinDate(),
                            item.getNote(),
                            item.getSource(),
                            item.getActivityLogId(),
                            item.getCreatedAt());
                })
                .toList();

        return new CheckinOverview(
                target,
                habitCount,
                doneCount,
                streak(allDates, target),
                monthCount,
                habitCheckinMapper.countDistinctDays(userId),
                responses,
                records);
    }

    public List<CheckinDaySummary> calendar(Long userId, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_CALENDAR_DAYS);
        if (start.isAfter(end)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "开始日期不能晚于结束日期");
        }
        return habitCheckinMapper.countByDay(userId, start, end);
    }

    @Transactional
    public CheckinResult check(Long userId, Long habitId, LocalDate date, String note) {
        Habit habit = requireOwnedHabit(userId, habitId);
        if (Boolean.FALSE.equals(habit.getActive())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "打卡项已停用，请先启用再打卡");
        }
        LocalDate target = date != null ? date : LocalDate.now();
        if (target.isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不能为未来日期打卡");
        }

        HabitCheckin existing = findCheckin(userId, habitId, target);
        if (existing != null) {
            return new CheckinResult(habitId, target, true, streakOf(userId, habitId, target),
                    existing.getActivityLogId());
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime occurredAt = target.equals(LocalDate.now())
                ? now
                : LocalDateTime.of(target, now.toLocalTime());
        String cleanNote = normalizeNote(note);

        HabitCheckin checkin = new HabitCheckin();
        checkin.setUserId(userId);
        checkin.setHabitId(habitId);
        checkin.setCheckinDate(target);
        checkin.setNote(cleanNote);
        checkin.setSource(SOURCE_CHECKIN);
        checkin.setCreatedAt(now);
        habitCheckinMapper.insert(checkin);

        ActivityLogResponse log = activityLogService.create(userId, new ActivityLogCreateRequest(
                buildContent(habit, cleanNote),
                target,
                occurredAt,
                habit.getCategory(),
                buildTags(habit),
                ActivityLogService.SOURCE_CHECKIN));

        checkin.setActivityLogId(log.id());
        habitCheckinMapper.updateById(checkin);

        return new CheckinResult(habitId, target, true, streakOf(userId, habitId, target), log.id());
    }

    @Transactional
    public CheckinResult cancel(Long userId, Long habitId, LocalDate date) {
        Habit habit = requireOwnedHabit(userId, habitId);
        LocalDate target = date != null ? date : LocalDate.now();
        HabitCheckin existing = findCheckin(userId, habitId, target);
        if (existing == null) {
            return new CheckinResult(habit.getId(), target, false, streakOf(userId, habitId, target), null);
        }
        activityLogService.deleteIfExists(userId, existing.getActivityLogId());
        habitCheckinMapper.deleteById(existing.getId());
        return new CheckinResult(habit.getId(), target, false, streakOf(userId, habitId, target), null);
    }

    @Transactional
    public Long createHabit(Long userId, HabitRequest request) {
        Habit habit = new Habit();
        habit.setUserId(userId);
        habit.setName(request.name().trim());
        habit.setFrequency("DAILY");
        habit.setTargetCount(1);
        habit.setActive(!Boolean.FALSE.equals(request.active()));
        habit.setIcon(normalizeIcon(request.icon()));
        habit.setColor(normalizeColor(request.color()));
        habit.setCategory(normalizeCategory(request.category()));
        habit.setNote(normalizeNote(request.note()));
        habit.setSortOrder(request.sortOrder() != null ? request.sortOrder() : nextSortOrder(userId));
        habit.setCreatedAt(LocalDateTime.now());
        habitMapper.insert(habit);
        return habit.getId();
    }

    @Transactional
    public void updateHabit(Long userId, Long id, HabitRequest request) {
        requireOwnedHabit(userId, id);
        LambdaUpdateWrapper<Habit> update = new LambdaUpdateWrapper<Habit>()
                .eq(Habit::getId, id)
                .eq(Habit::getUserId, userId)
                .set(Habit::getName, request.name().trim());
        if (request.icon() != null) {
            update.set(Habit::getIcon, normalizeIcon(request.icon()));
        }
        if (request.color() != null) {
            update.set(Habit::getColor, normalizeColor(request.color()));
        }
        if (request.category() != null) {
            update.set(Habit::getCategory, normalizeCategory(request.category()));
        }
        if (request.note() != null) {
            update.set(Habit::getNote, normalizeNote(request.note()));
        }
        if (request.active() != null) {
            update.set(Habit::getActive, request.active());
        }
        if (request.sortOrder() != null) {
            update.set(Habit::getSortOrder, request.sortOrder());
        }
        habitMapper.update(null, update);
    }

    /**
     * 已有打卡历史时只停用，避免连带删掉同步生成的活动记录；没有历史才物理删除。
     */
    @Transactional
    public HabitRemovalResult removeHabit(Long userId, Long id) {
        Habit habit = requireOwnedHabit(userId, id);
        long checkinCount = habitCheckinMapper.selectCount(new LambdaQueryWrapper<HabitCheckin>()
                .eq(HabitCheckin::getUserId, userId)
                .eq(HabitCheckin::getHabitId, id));
        if (checkinCount > 0) {
            habit.setActive(false);
            habitMapper.updateById(habit);
            return new HabitRemovalResult(true);
        }
        habitMapper.deleteById(id);
        return new HabitRemovalResult(false);
    }

    private Habit requireOwnedHabit(Long userId, Long habitId) {
        Habit habit = habitMapper.selectById(habitId);
        if (habit == null || !habit.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "打卡项不存在");
        }
        return habit;
    }

    private HabitCheckin findCheckin(Long userId, Long habitId, LocalDate date) {
        return habitCheckinMapper.selectOne(new LambdaQueryWrapper<HabitCheckin>()
                .eq(HabitCheckin::getUserId, userId)
                .eq(HabitCheckin::getHabitId, habitId)
                .eq(HabitCheckin::getCheckinDate, date)
                .last("limit 1"));
    }

    private int streakOf(Long userId, Long habitId, LocalDate target) {
        List<HabitCheckin> items = habitCheckinMapper.selectList(new LambdaQueryWrapper<HabitCheckin>()
                .eq(HabitCheckin::getUserId, userId)
                .eq(HabitCheckin::getHabitId, habitId)
                .ge(HabitCheckin::getCheckinDate, target.minusDays(HISTORY_DAYS))
                .le(HabitCheckin::getCheckinDate, target));
        Set<LocalDate> dates = items.stream()
                .map(HabitCheckin::getCheckinDate)
                .collect(Collectors.toCollection(TreeSet::new));
        return streak(dates, target);
    }

    /**
     * 连续天数：当天已打卡就从当天往前数，否则从昨天往前数，中途断档即止。
     */
    private static int streak(Set<LocalDate> dates, LocalDate target) {
        if (dates.isEmpty()) {
            return 0;
        }
        LocalDate cursor = dates.contains(target) ? target : target.minusDays(1);
        int days = 0;
        while (dates.contains(cursor)) {
            days++;
            cursor = cursor.minusDays(1);
        }
        return days;
    }

    private String buildContent(Habit habit, String note) {
        String base = TAG_CHECKIN + "：" + habit.getName();
        return note == null ? base : base + " · " + note;
    }

    private String buildTags(Habit habit) {
        String tag = habit.getName().replace(",", " ").replace("，", " ").trim();
        if (tag.length() > MAX_TAG_LENGTH) {
            tag = tag.substring(0, MAX_TAG_LENGTH);
        }
        return tag.isEmpty() ? TAG_CHECKIN : TAG_CHECKIN + "," + tag;
    }

    private String normalizeIcon(String icon) {
        if (icon == null || icon.isBlank()) {
            return null;
        }
        String value = icon.trim();
        return value.length() > MAX_ICON_LENGTH ? value.substring(0, MAX_ICON_LENGTH) : value;
    }

    private String normalizeColor(String color) {
        if (color == null || color.isBlank()) {
            return DEFAULT_COLOR;
        }
        String value = color.trim();
        return COLOR_PATTERN.matcher(value).matches() ? value : DEFAULT_COLOR;
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return ActivityLogService.CATEGORY_OTHER;
        }
        String value = category.trim().toUpperCase(Locale.ROOT);
        return CATEGORIES.contains(value) ? value : ActivityLogService.CATEGORY_OTHER;
    }

    private String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        String value = note.trim();
        if (value.isEmpty()) {
            return null;
        }
        return value.length() > MAX_NOTE_LENGTH ? value.substring(0, MAX_NOTE_LENGTH) : value;
    }

    private int nextSortOrder(Long userId) {
        Habit last = habitMapper.selectOne(new LambdaQueryWrapper<Habit>()
                .eq(Habit::getUserId, userId)
                .orderByDesc(Habit::getSortOrder)
                .last("limit 1"));
        return last == null || last.getSortOrder() == null ? 0 : last.getSortOrder() + 1;
    }
}
