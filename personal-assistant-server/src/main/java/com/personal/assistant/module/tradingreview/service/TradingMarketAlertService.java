package com.personal.assistant.module.tradingreview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.reminder.entity.NotificationChannel;
import com.personal.assistant.module.reminder.mapper.NotificationChannelMapper;
import com.personal.assistant.module.reminder.service.NotificationService;
import com.personal.assistant.module.tradingreview.dto.TradingMarketAlertRuleRequest;
import com.personal.assistant.module.tradingreview.dto.TradingMarketAlertScanResponse;
import com.personal.assistant.module.tradingreview.entity.TradingDailyReview;
import com.personal.assistant.module.tradingreview.entity.TradingMarketAlertEvent;
import com.personal.assistant.module.tradingreview.entity.TradingMarketAlertRule;
import com.personal.assistant.module.tradingreview.mapper.TradingDailyReviewMapper;
import com.personal.assistant.module.tradingreview.mapper.TradingMarketAlertEventMapper;
import com.personal.assistant.module.tradingreview.mapper.TradingMarketAlertRuleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TradingMarketAlertService {
    private static final Logger log = LoggerFactory.getLogger(TradingMarketAlertService.class);
    private static final Set<String> RULE_TYPES = Set.of("MARKET_METRIC", "SECTOR_METRIC");
    private static final Set<String> SNAPSHOT_TYPES = Set.of("REALTIME", "FINAL", "ANY");
    private static final Set<String> DIRECTIONS = Set.of("ABOVE", "BELOW");
    private static final Set<String> MARKET_METRICS = Set.of("SENTIMENT_SCORE", "BROKEN_BOARD_RATE", "LIMIT_DOWN_COUNT",
            "LIMIT_UP_COUNT", "RISING_COUNT", "FALLING_COUNT", "TURNOVER_CHANGE", "MAX_STREAK", "MARKET_MEDIAN_CHANGE");
    private static final Set<String> SECTOR_METRICS = Set.of("CHANGE", "TURNOVER", "LIMIT_UP_COUNT", "RISING", "FALLING");

    private final TradingMarketAlertRuleMapper rules;
    private final TradingMarketAlertEventMapper events;
    private final TradingDailyReviewMapper reviews;
    private final NotificationChannelMapper channels;
    private final NotificationService notifications;
    private final ObjectMapper json;

    public TradingMarketAlertService(TradingMarketAlertRuleMapper rules, TradingMarketAlertEventMapper events,
                                     TradingDailyReviewMapper reviews, NotificationChannelMapper channels,
                                     NotificationService notifications, ObjectMapper json) {
        this.rules = rules;
        this.events = events;
        this.reviews = reviews;
        this.channels = channels;
        this.notifications = notifications;
        this.json = json;
    }

    public List<TradingMarketAlertRule> listRules(Long userId, Boolean enabled) {
        return rules.selectList(new LambdaQueryWrapper<TradingMarketAlertRule>()
                .eq(TradingMarketAlertRule::getUserId, userId)
                .eq(enabled != null, TradingMarketAlertRule::getEnabled, enabled)
                .orderByDesc(TradingMarketAlertRule::getUpdatedAt)
                .orderByDesc(TradingMarketAlertRule::getId));
    }

    public List<TradingMarketAlertEvent> listEvents(Long userId, Long ruleId) {
        return events.selectList(new LambdaQueryWrapper<TradingMarketAlertEvent>()
                .eq(TradingMarketAlertEvent::getUserId, userId)
                .eq(ruleId != null, TradingMarketAlertEvent::getRuleId, ruleId)
                .orderByDesc(TradingMarketAlertEvent::getTriggeredAt)
                .last("limit 200"));
    }

    @Transactional
    public Long saveRule(Long userId, Long id, TradingMarketAlertRuleRequest request) {
        String type = normalize(request.ruleType());
        String snapshotType = StringUtils.hasText(request.snapshotType()) ? normalize(request.snapshotType()) : "ANY";
        String metric = normalize(request.metricKey());
        String direction = normalize(request.direction());
        if (!RULE_TYPES.contains(type)) throw validation("Unsupported market alert rule type");
        if (!SNAPSHOT_TYPES.contains(snapshotType)) throw validation("Unsupported snapshot type");
        if (!DIRECTIONS.contains(direction)) throw validation("Unsupported direction");
        if ("MARKET_METRIC".equals(type) && !MARKET_METRICS.contains(metric)) throw validation("Unsupported market metric");
        if ("SECTOR_METRIC".equals(type) && (!SECTOR_METRICS.contains(metric) || !StringUtils.hasText(request.sectorName()))) {
            throw validation("Sector metric rule requires supported metricKey and sectorName");
        }
        if (request.validFrom() != null && request.validTo() != null && request.validTo().isBefore(request.validFrom())) {
            throw validation("validTo must not be before validFrom");
        }
        TradingMarketAlertRule rule = id == null ? new TradingMarketAlertRule() : requireRule(userId, id);
        if (id == null) {
            rule.setUserId(userId);
            rule.setStatus("MONITORING");
            rule.setCreatedAt(now());
        }
        rule.setRuleType(type);
        rule.setSnapshotType(snapshotType);
        rule.setMetricKey(metric);
        rule.setDirection(direction);
        rule.setThresholdValue(request.thresholdValue());
        rule.setSectorName(trimToNull(request.sectorName()));
        rule.setSectorLevel(request.sectorLevel());
        rule.setTitle(title(request, type, metric, direction));
        rule.setNote(trimToNull(request.note()));
        rule.setEnabled(!Boolean.FALSE.equals(request.enabled()));
        rule.setOnceOnly(!Boolean.FALSE.equals(request.onceOnly()));
        rule.setValidFrom(request.validFrom());
        rule.setValidTo(request.validTo());
        rule.setUpdatedAt(now());
        if (id == null) rules.insert(rule); else rules.updateById(rule);
        return rule.getId();
    }

    @Transactional
    public void toggleRule(Long userId, Long id, boolean enabled) {
        TradingMarketAlertRule rule = requireRule(userId, id);
        rule.setEnabled(enabled);
        rule.setStatus(enabled ? "MONITORING" : "DISABLED");
        rule.setUpdatedAt(now());
        rules.updateById(rule);
    }

    @Transactional
    public void deleteRule(Long userId, Long id) {
        TradingMarketAlertRule rule = requireRule(userId, id);
        events.delete(new LambdaQueryWrapper<TradingMarketAlertEvent>()
                .eq(TradingMarketAlertEvent::getUserId, userId)
                .eq(TradingMarketAlertEvent::getRuleId, id));
        rules.deleteById(rule);
    }

    @Transactional
    public TradingMarketAlertScanResponse scanUser(Long userId) {
        return scanRules(listRules(userId, true));
    }

    @Transactional
    public TradingMarketAlertScanResponse scanReview(Long userId, TradingDailyReview review) {
        List<TradingMarketAlertRule> ruleList = rules.selectList(new LambdaQueryWrapper<TradingMarketAlertRule>()
                .eq(TradingMarketAlertRule::getUserId, userId)
                .eq(TradingMarketAlertRule::getEnabled, true)
                .and(q -> q.eq(TradingMarketAlertRule::getSnapshotType, "ANY")
                        .or().eq(TradingMarketAlertRule::getSnapshotType, review.getSnapshotType())));
        return scanRules(ruleList, review);
    }

    @Scheduled(cron = "0 */5 9-15 * * MON-FRI", zone = "Asia/Shanghai")
    public void scheduledScan() {
        try {
            scanRules(rules.selectList(new LambdaQueryWrapper<TradingMarketAlertRule>()
                    .eq(TradingMarketAlertRule::getEnabled, true)
                    .orderByAsc(TradingMarketAlertRule::getUserId)
                    .orderByAsc(TradingMarketAlertRule::getId)));
        } catch (RuntimeException exception) {
            log.warn("Trading market alert scan failed", exception);
        }
    }

    private TradingMarketAlertScanResponse scanRules(List<TradingMarketAlertRule> ruleList) {
        int checked = 0;
        int triggered = 0;
        int skipped = 0;
        for (Map.Entry<Long, List<TradingMarketAlertRule>> entry : ruleList.stream().collect(Collectors.groupingBy(TradingMarketAlertRule::getUserId)).entrySet()) {
            TradingDailyReview review = latestReview(entry.getKey());
            if (review == null) {
                skipped += entry.getValue().size();
                continue;
            }
            TradingMarketAlertScanResponse response = scanRules(entry.getValue(), review);
            checked += response.checkedRules();
            triggered += response.triggeredEvents();
            skipped += response.skippedRules();
        }
        return new TradingMarketAlertScanResponse(checked, triggered, skipped);
    }

    private TradingMarketAlertScanResponse scanRules(List<TradingMarketAlertRule> ruleList, TradingDailyReview review) {
        int checked = 0;
        int triggered = 0;
        int skipped = 0;
        NotificationChannel channel = preferredChannel(review.getUserId());
        for (TradingMarketAlertRule rule : ruleList) {
            if (!activeToday(rule) || !matchesSnapshot(rule, review)) {
                skipped++;
                continue;
            }
            BigDecimal observed = observedValue(rule, review);
            if (observed == null) {
                markChecked(rule, null, review.getId(), "NO_DATA");
                skipped++;
                continue;
            }
            checked++;
            boolean hit = hit(rule, observed);
            markChecked(rule, observed, review.getId(), hit ? "TRIGGERED" : "MONITORING");
            if (hit && shouldCreateEvent(rule, observed, review)) {
                createEvent(rule, review, observed, channel);
                triggered++;
            }
        }
        return new TradingMarketAlertScanResponse(checked, triggered, skipped);
    }

    private BigDecimal observedValue(TradingMarketAlertRule rule, TradingDailyReview review) {
        if ("MARKET_METRIC".equals(rule.getRuleType())) return marketMetric(rule.getMetricKey(), review);
        return sectorMetric(rule, review);
    }

    private BigDecimal marketMetric(String metric, TradingDailyReview review) {
        return switch (metric) {
            case "SENTIMENT_SCORE" -> review.getSentimentScore();
            case "BROKEN_BOARD_RATE" -> review.getBrokenBoardRate();
            case "LIMIT_DOWN_COUNT" -> value(review.getLimitDownCount());
            case "LIMIT_UP_COUNT" -> value(review.getLimitUpCount());
            case "RISING_COUNT" -> value(review.getRisingCount());
            case "FALLING_COUNT" -> value(review.getFallingCount());
            case "TURNOVER_CHANGE" -> review.getTurnoverChange();
            case "MAX_STREAK" -> value(review.getMaxStreak());
            case "MARKET_MEDIAN_CHANGE" -> jsonDecimal(review.getRawMetrics(), "marketMedian", "change");
            default -> null;
        };
    }

    private BigDecimal sectorMetric(TradingMarketAlertRule rule, TradingDailyReview review) {
        try {
            JsonNode rankings = json.readTree(review.getRawMetrics()).path("sectorRankings");
            for (String listName : sectorLists(rule)) {
                for (JsonNode sector : rankings.path(listName)) {
                    if (matchesSector(rule, sector)) return sectorValue(rule.getMetricKey(), sector);
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private List<String> sectorLists(TradingMarketAlertRule rule) {
        Integer level = rule.getSectorLevel();
        if (level != null && level == 2) return List.of("turnoverL2", "risingL2", "fallingL2", "limitUp");
        if (level != null && level == 3) return List.of("turnoverL3", "risingL3", "fallingL3", "limitUp");
        return List.of("turnover", "rising", "falling", "limitUp");
    }

    private boolean matchesSector(TradingMarketAlertRule rule, JsonNode sector) {
        String actual = sector.path("name").asText("");
        boolean nameMatches = actual.equals(rule.getSectorName());
        if (!nameMatches) return false;
        return rule.getSectorLevel() == null || sector.path("level").isMissingNode()
                || sector.path("level").asInt(rule.getSectorLevel()) == rule.getSectorLevel();
    }

    private BigDecimal sectorValue(String metric, JsonNode sector) {
        return switch (metric) {
            case "CHANGE" -> decimal(sector.path("change"));
            case "TURNOVER" -> decimal(sector.path("turnover"));
            case "LIMIT_UP_COUNT" -> decimal(sector.path("limitUpCount"));
            case "RISING" -> decimal(sector.path("rising"));
            case "FALLING" -> decimal(sector.path("falling"));
            default -> null;
        };
    }

    private void createEvent(TradingMarketAlertRule rule, TradingDailyReview review, BigDecimal observed, NotificationChannel channel) {
        TradingMarketAlertEvent event = new TradingMarketAlertEvent();
        event.setUserId(rule.getUserId());
        event.setRuleId(rule.getId());
        event.setReviewId(review.getId());
        event.setTradeDate(review.getTradeDate());
        event.setSnapshotType(review.getSnapshotType());
        event.setRuleType(rule.getRuleType());
        event.setMetricKey(rule.getMetricKey());
        event.setDirection(rule.getDirection());
        event.setThresholdValue(rule.getThresholdValue());
        event.setObservedValue(observed);
        event.setSectorName(rule.getSectorName());
        event.setTitle(rule.getTitle());
        event.setContent(content(rule, review, observed));
        event.setTriggeredAt(now());
        if (channel == null) {
            event.setNotificationStatus("NO_CHANNEL");
            event.setNotificationMessage("No enabled notification channel");
        } else {
            try {
                notifications.send(rule.getUserId(), null, channel.getId(), event.getTitle(), event.getContent());
                event.setNotificationStatus("SENT");
                event.setNotificationMessage("Sent");
            } catch (RuntimeException exception) {
                event.setNotificationStatus("FAILED");
                event.setNotificationMessage(exception.getMessage());
            }
        }
        events.insert(event);
        rule.setLastTriggeredAt(event.getTriggeredAt());
        if (Boolean.TRUE.equals(rule.getOnceOnly())) {
            rule.setEnabled(false);
            rule.setStatus("TRIGGERED");
        }
        rule.setUpdatedAt(now());
        rules.updateById(rule);
    }

    private boolean shouldCreateEvent(TradingMarketAlertRule rule, BigDecimal observed, TradingDailyReview review) {
        if (rule.getLastTriggeredAt() == null) return true;
        if (Boolean.TRUE.equals(rule.getOnceOnly())) return false;
        if (rule.getLastReviewId() != null && rule.getLastReviewId().equals(review.getId())) return false;
        BigDecimal previous = rule.getLastObservedValue();
        return previous == null || (!hit(rule, previous) && hit(rule, observed));
    }

    private boolean hit(TradingMarketAlertRule rule, BigDecimal observed) {
        int compared = observed.compareTo(rule.getThresholdValue());
        return "ABOVE".equals(rule.getDirection()) ? compared >= 0 : compared <= 0;
    }

    private void markChecked(TradingMarketAlertRule rule, BigDecimal observed, Long reviewId, String status) {
        rule.setLastCheckedAt(now());
        rule.setLastObservedValue(observed);
        rule.setLastReviewId(reviewId);
        rule.setStatus(status);
        rule.setUpdatedAt(now());
        rules.updateById(rule);
    }

    private TradingDailyReview latestReview(Long userId) {
        return reviews.selectOne(new LambdaQueryWrapper<TradingDailyReview>()
                .eq(TradingDailyReview::getUserId, userId)
                .eq(TradingDailyReview::getCollectionStatus, "SUCCESS")
                .orderByDesc(TradingDailyReview::getTradeDate)
                .orderByDesc(TradingDailyReview::getQuoteTime)
                .orderByDesc(TradingDailyReview::getUpdatedAt)
                .last("limit 1"));
    }

    private boolean activeToday(TradingMarketAlertRule rule) {
        LocalDate today = LocalDate.now();
        return (rule.getValidFrom() == null || !today.isBefore(rule.getValidFrom()))
                && (rule.getValidTo() == null || !today.isAfter(rule.getValidTo()));
    }

    private boolean matchesSnapshot(TradingMarketAlertRule rule, TradingDailyReview review) {
        return "ANY".equals(rule.getSnapshotType()) || rule.getSnapshotType().equals(review.getSnapshotType());
    }

    private NotificationChannel preferredChannel(Long userId) {
        return channels.selectList(new LambdaQueryWrapper<NotificationChannel>()
                        .eq(NotificationChannel::getUserId, userId)
                        .eq(NotificationChannel::getEnabled, true))
                .stream().min(channelPreference()).orElse(null);
    }

    private Comparator<NotificationChannel> channelPreference() {
        return Comparator.comparing((NotificationChannel channel) ->
                        "SERVER_CHAN".equals(channel.getChannelType()) ? 0 : 1)
                .thenComparing(NotificationChannel::getId);
    }

    private TradingMarketAlertRule requireRule(Long userId, Long id) {
        TradingMarketAlertRule rule = rules.selectById(id);
        if (rule == null || !userId.equals(rule.getUserId())) throw new BusinessException(ErrorCode.NOT_FOUND, "Market alert rule not found");
        return rule;
    }

    private String title(TradingMarketAlertRuleRequest request, String type, String metric, String direction) {
        if (StringUtils.hasText(request.title())) return request.title().trim();
        String prefix = "SECTOR_METRIC".equals(type) ? request.sectorName() + " " : "";
        return prefix + metric + " " + direction + " " + request.thresholdValue().stripTrailingZeros().toPlainString();
    }

    private String content(TradingMarketAlertRule rule, TradingDailyReview review, BigDecimal observed) {
        String target = "SECTOR_METRIC".equals(rule.getRuleType()) ? rule.getSectorName() : "MARKET";
        return "%s %s triggered on %s %s. observed=%s, threshold=%s. %s".formatted(
                target,
                rule.getMetricKey(),
                review.getTradeDate(),
                review.getSnapshotType(),
                observed.stripTrailingZeros().toPlainString(),
                rule.getThresholdValue().stripTrailingZeros().toPlainString(),
                rule.getNote() == null ? "" : rule.getNote());
    }

    private BigDecimal jsonDecimal(String raw, String objectName, String fieldName) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            return decimal(json.readTree(raw).path(objectName).path(fieldName));
        } catch (Exception ignored) {
            return null;
        }
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        try {
            return new BigDecimal(node.asText());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BigDecimal value(Integer value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }
}
