package com.personal.assistant.module.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.activity.dto.ActivityDaySummary;
import com.personal.assistant.module.activity.dto.ActivityLogCreateRequest;
import com.personal.assistant.module.activity.dto.ActivityLogResponse;
import com.personal.assistant.module.activity.dto.ActivityLogUpdateRequest;
import com.personal.assistant.module.activity.dto.ActivityTagSummary;
import com.personal.assistant.module.activity.entity.ActivityLog;
import com.personal.assistant.module.activity.mapper.ActivityLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 每日活动记录服务。只做记录本身：新增、编辑、删除、按天回溯与聚合，不做分析也不参与其他模块的转换流程。
 */
@Service
public class ActivityLogService {

    public static final String CATEGORY_OTHER = "OTHER";
    public static final String SOURCE_WEB = "WEB";

    private static final Set<String> CATEGORIES =
            Set.of("WORK", "STUDY", "EXERCISE", "FOOD", "SOCIAL", "REST", "OTHER");
    private static final Set<String> SOURCES = Set.of("WEB", "MOBILE_WEB", "API", "AGENT");

    private static final int MAX_TAG_LENGTH = 50;
    private static final int MAX_TAGS_LENGTH = 500;
    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 500;
    private static final int DEFAULT_CALENDAR_DAYS = 365;

    private final ActivityLogMapper activityLogMapper;

    public ActivityLogService(ActivityLogMapper activityLogMapper) {
        this.activityLogMapper = activityLogMapper;
    }

    @Transactional
    public ActivityLogResponse create(Long userId, ActivityLogCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime occurredAt = request.occurredAt() != null ? request.occurredAt() : now;

        ActivityLog log = new ActivityLog();
        log.setUserId(userId);
        log.setContent(request.content().trim());
        log.setOccurredAt(occurredAt);
        log.setLogDate(request.logDate() != null ? request.logDate() : occurredAt.toLocalDate());
        log.setCategory(normalizeCategory(request.category()));
        log.setTags(normalizeTags(request.tags()));
        log.setSource(normalizeSource(request.source()));
        log.setPinned(Boolean.FALSE);
        log.setCreatedAt(now);
        log.setUpdatedAt(now);

        activityLogMapper.insert(log);
        return toResponse(log);
    }

    public List<ActivityLogResponse> list(Long userId,
                                          LocalDate from,
                                          LocalDate to,
                                          String keyword,
                                          String category,
                                          String tag,
                                          Integer limit) {
        LambdaQueryWrapper<ActivityLog> wrapper = new LambdaQueryWrapper<ActivityLog>()
                .eq(ActivityLog::getUserId, userId);
        if (from != null) {
            wrapper.ge(ActivityLog::getLogDate, from);
        }
        if (to != null) {
            wrapper.le(ActivityLog::getLogDate, to);
        }
        if (category != null && !category.isBlank()) {
            wrapper.eq(ActivityLog::getCategory, category.trim().toUpperCase(Locale.ROOT));
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(ActivityLog::getContent, keyword.trim());
        }
        if (tag != null && !tag.isBlank()) {
            wrapper.apply("(',' || coalesce(tags, '') || ',') like {0}", "%," + escapeLike(tag.trim()) + ",%");
        }
        wrapper.orderByDesc(ActivityLog::getPinned)
                .orderByDesc(ActivityLog::getOccurredAt)
                .orderByDesc(ActivityLog::getId)
                .last("limit " + resolveLimit(limit));
        return activityLogMapper.selectList(wrapper).stream().map(ActivityLogService::toResponse).toList();
    }

    public List<ActivityLogResponse> timeline(Long userId, LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        return activityLogMapper.selectList(new LambdaQueryWrapper<ActivityLog>()
                        .eq(ActivityLog::getUserId, userId)
                        .eq(ActivityLog::getLogDate, target)
                        .orderByDesc(ActivityLog::getPinned)
                        .orderByDesc(ActivityLog::getOccurredAt)
                        .orderByDesc(ActivityLog::getId))
                .stream().map(ActivityLogService::toResponse).toList();
    }

    public List<ActivityDaySummary> calendar(Long userId, LocalDate from, LocalDate to) {
        LocalDate start = from != null ? from : LocalDate.now().minusDays(DEFAULT_CALENDAR_DAYS);
        LocalDate end = to != null ? to : LocalDate.now();
        if (start.isAfter(end)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "开始日期不能晚于结束日期");
        }
        return activityLogMapper.countByDay(userId, start, end);
    }

    public List<ActivityTagSummary> tags(Long userId, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(90);
        if (start.isAfter(end)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "开始日期不能晚于结束日期");
        }
        return activityLogMapper.countByTag(userId, start, end);
    }

    @Transactional
    public ActivityLogResponse update(Long userId, Long id, ActivityLogUpdateRequest request) {
        ActivityLog log = requireOwned(userId, id);

        log.setContent(request.content().trim());
        if (request.occurredAt() != null) {
            log.setOccurredAt(request.occurredAt());
        }
        if (request.logDate() != null) {
            log.setLogDate(request.logDate());
        } else if (request.occurredAt() != null) {
            log.setLogDate(request.occurredAt().toLocalDate());
        }
        if (request.category() != null) {
            log.setCategory(normalizeCategory(request.category()));
        }
        log.setTags(normalizeTags(request.tags()));
        if (request.pinned() != null) {
            log.setPinned(request.pinned());
        }
        log.setUpdatedAt(LocalDateTime.now());

        activityLogMapper.updateById(log);
        return toResponse(log);
    }

    @Transactional
    public ActivityLogResponse togglePin(Long userId, Long id) {
        ActivityLog log = requireOwned(userId, id);
        log.setPinned(!Boolean.TRUE.equals(log.getPinned()));
        log.setUpdatedAt(LocalDateTime.now());
        activityLogMapper.updateById(log);
        return toResponse(log);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        ActivityLog log = requireOwned(userId, id);
        activityLogMapper.deleteById(log.getId());
    }

    private ActivityLog requireOwned(Long userId, Long id) {
        ActivityLog log = activityLogMapper.selectById(id);
        if (log == null || !log.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "活动记录不存在");
        }
        return log;
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return CATEGORY_OTHER;
        }
        String value = category.trim().toUpperCase(Locale.ROOT);
        return CATEGORIES.contains(value) ? value : CATEGORY_OTHER;
    }

    private String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return SOURCE_WEB;
        }
        String value = source.trim().toUpperCase(Locale.ROOT);
        return SOURCES.contains(value) ? value : SOURCE_WEB;
    }

    /**
     * 标签统一为逗号分隔、去空格、去重、单项不超过 50 字、整体不超过 500 字，便于按标签精确过滤。
     */
    private String normalizeTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return null;
        }
        Set<String> unique = new LinkedHashSet<>();
        StringBuilder builder = new StringBuilder();
        for (String raw : tags.split("[,，]")) {
            String tag = raw.trim();
            if (tag.isEmpty() || tag.length() > MAX_TAG_LENGTH || !unique.add(tag)) {
                continue;
            }
            int nextLength = builder.length() + tag.length() + (builder.isEmpty() ? 0 : 1);
            if (nextLength > MAX_TAGS_LENGTH) {
                break;
            }
            if (!builder.isEmpty()) {
                builder.append(',');
            }
            builder.append(tag);
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static ActivityLogResponse toResponse(ActivityLog log) {
        return new ActivityLogResponse(
                log.getId(),
                log.getContent(),
                log.getLogDate(),
                log.getOccurredAt(),
                log.getCategory(),
                log.getTags(),
                log.getSource(),
                log.getPinned(),
                log.getCreatedAt(),
                log.getUpdatedAt());
    }
}
