package com.personal.assistant.module.tradingreview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.reminder.entity.NotificationChannel;
import com.personal.assistant.module.reminder.mapper.NotificationChannelMapper;
import com.personal.assistant.module.reminder.service.NotificationService;
import com.personal.assistant.module.stock.entity.StockWatchItem;
import com.personal.assistant.module.stock.mapper.StockWatchItemMapper;
import com.personal.assistant.module.tradingreview.dto.TradingAlertRuleRequest;
import com.personal.assistant.module.tradingreview.dto.TradingAlertScanResponse;
import com.personal.assistant.module.tradingreview.entity.TradingAlertEvent;
import com.personal.assistant.module.tradingreview.entity.TradingAlertRule;
import com.personal.assistant.module.tradingreview.entity.TradingNextPlan;
import com.personal.assistant.module.tradingreview.mapper.TradingAlertEventMapper;
import com.personal.assistant.module.tradingreview.mapper.TradingAlertRuleMapper;
import com.personal.assistant.module.tradingreview.mapper.TradingNextPlanMapper;
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
public class TradingAlertService {
    private static final Logger log = LoggerFactory.getLogger(TradingAlertService.class);
    private static final Set<String> RULE_TYPES = Set.of("PRICE", "CHANGE_PERCENT", "STOP_LOSS", "TAKE_PROFIT", "POSITION_DEVIATION");
    private static final Set<String> DIRECTIONS = Set.of("ABOVE", "BELOW");

    private final TradingAlertRuleMapper rules;
    private final TradingAlertEventMapper events;
    private final StockWatchItemMapper watches;
    private final TradingNextPlanMapper plans;
    private final NotificationChannelMapper channels;
    private final NotificationService notifications;

    public TradingAlertService(TradingAlertRuleMapper rules, TradingAlertEventMapper events, StockWatchItemMapper watches,
                               TradingNextPlanMapper plans, NotificationChannelMapper channels,
                               NotificationService notifications) {
        this.rules = rules;
        this.events = events;
        this.watches = watches;
        this.plans = plans;
        this.channels = channels;
        this.notifications = notifications;
    }

    public List<TradingAlertRule> listRules(Long userId, Boolean enabled) {
        return rules.selectList(new LambdaQueryWrapper<TradingAlertRule>()
                .eq(TradingAlertRule::getUserId, userId)
                .eq(enabled != null, TradingAlertRule::getEnabled, enabled)
                .orderByDesc(TradingAlertRule::getUpdatedAt)
                .orderByDesc(TradingAlertRule::getId));
    }

    public List<TradingAlertEvent> listEvents(Long userId, Long ruleId) {
        return events.selectList(new LambdaQueryWrapper<TradingAlertEvent>()
                .eq(TradingAlertEvent::getUserId, userId)
                .eq(ruleId != null, TradingAlertEvent::getRuleId, ruleId)
                .orderByDesc(TradingAlertEvent::getTriggeredAt)
                .last("limit 200"));
    }

    @Transactional
    public Long saveRule(Long userId, Long id, TradingAlertRuleRequest request) {
        String type = normalize(request.ruleType());
        String direction = normalize(request.direction());
        if (!RULE_TYPES.contains(type)) throw validation("Unsupported trading alert rule type");
        if (!DIRECTIONS.contains(direction)) throw validation("Unsupported trading alert direction");
        if ("STOP_LOSS".equals(type)) direction = "BELOW";
        if ("TAKE_PROFIT".equals(type)) direction = "ABOVE";
        if (request.validFrom() != null && request.validTo() != null && request.validTo().isBefore(request.validFrom())) {
            throw validation("validTo must not be before validFrom");
        }

        TradingAlertRule rule = id == null ? new TradingAlertRule() : requireRule(userId, id);
        StockWatchItem watch = request.watchItemId() == null ? null : requireWatch(userId, request.watchItemId());
        if (request.planId() != null) requirePlan(userId, request.planId());
        if (id == null) {
            rule.setUserId(userId);
            rule.setStatus("MONITORING");
            rule.setCreatedAt(now());
        }
        rule.setWatchItemId(watch == null ? null : watch.getId());
        rule.setPlanId(request.planId());
        rule.setStockCode(stockCode(request, watch));
        rule.setStockName(stockName(request, watch));
        rule.setRuleType(type);
        rule.setDirection(direction);
        rule.setThresholdValue(request.thresholdValue());
        rule.setReferencePosition(request.referencePosition());
        rule.setTitle(title(request, type, direction, rule.getStockName(), request.thresholdValue()));
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
        TradingAlertRule rule = requireRule(userId, id);
        rule.setEnabled(enabled);
        rule.setStatus(enabled ? "MONITORING" : "DISABLED");
        rule.setUpdatedAt(now());
        rules.updateById(rule);
    }

    @Transactional
    public void deleteRule(Long userId, Long id) {
        TradingAlertRule rule = requireRule(userId, id);
        events.delete(new LambdaQueryWrapper<TradingAlertEvent>()
                .eq(TradingAlertEvent::getUserId, userId)
                .eq(TradingAlertEvent::getRuleId, id));
        rules.deleteById(rule);
    }

    @Transactional
    public TradingAlertScanResponse scanUser(Long userId) {
        return scanRules(listRules(userId, true));
    }

    @Scheduled(cron = "0 */5 9-15 * * MON-FRI", zone = "Asia/Shanghai")
    public void scheduledScan() {
        try {
            scanRules(rules.selectList(new LambdaQueryWrapper<TradingAlertRule>()
                    .eq(TradingAlertRule::getEnabled, true)
                    .orderByAsc(TradingAlertRule::getUserId)
                    .orderByAsc(TradingAlertRule::getId)));
        } catch (RuntimeException exception) {
            log.warn("Trading alert scan failed", exception);
        }
    }

    private TradingAlertScanResponse scanRules(List<TradingAlertRule> ruleList) {
        int checked = 0;
        int triggered = 0;
        int skipped = 0;
        Map<Long, NotificationChannel> channelByUser = preferredChannels();
        for (TradingAlertRule rule : ruleList) {
            if (!activeToday(rule)) {
                skipped++;
                continue;
            }
            StockWatchItem watch = rule.getWatchItemId() == null ? null : watches.selectById(rule.getWatchItemId());
            if (watch == null || !rule.getUserId().equals(watch.getUserId())) {
                markChecked(rule, null, "NO_QUOTE");
                skipped++;
                continue;
            }
            BigDecimal observed = observedValue(rule, watch);
            if (observed == null) {
                markChecked(rule, null, "NO_QUOTE");
                skipped++;
                continue;
            }
            checked++;
            boolean hit = hit(rule, observed);
            markChecked(rule, observed, hit ? "TRIGGERED" : "MONITORING");
            if (hit && shouldCreateEvent(rule, observed)) {
                createEvent(rule, watch, observed, channelByUser.get(rule.getUserId()));
                triggered++;
            }
        }
        return new TradingAlertScanResponse(checked, triggered, skipped);
    }

    private void createEvent(TradingAlertRule rule, StockWatchItem watch, BigDecimal observed, NotificationChannel channel) {
        TradingAlertEvent event = new TradingAlertEvent();
        event.setUserId(rule.getUserId());
        event.setRuleId(rule.getId());
        event.setWatchItemId(watch.getId());
        event.setStockCode(watch.getStockCode());
        event.setStockName(watch.getStockName());
        event.setRuleType(rule.getRuleType());
        event.setDirection(rule.getDirection());
        event.setThresholdValue(rule.getThresholdValue());
        event.setObservedValue(observed);
        event.setLatestPrice(watch.getLatestPrice());
        event.setChangePercent(watch.getChangePercent());
        event.setTitle(rule.getTitle());
        event.setContent(content(rule, watch, observed));
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

    private boolean shouldCreateEvent(TradingAlertRule rule, BigDecimal observed) {
        if (rule.getLastTriggeredAt() == null) return true;
        if (Boolean.TRUE.equals(rule.getOnceOnly())) return false;
        BigDecimal previous = rule.getLastObservedValue();
        if (previous == null) return true;
        return !hit(rule, previous) && hit(rule, observed);
    }

    private BigDecimal observedValue(TradingAlertRule rule, StockWatchItem watch) {
        return switch (rule.getRuleType()) {
            case "PRICE", "STOP_LOSS", "TAKE_PROFIT" -> watch.getLatestPrice();
            case "CHANGE_PERCENT" -> watch.getChangePercent();
            case "POSITION_DEVIATION" -> rule.getReferencePosition();
            default -> null;
        };
    }

    private boolean hit(TradingAlertRule rule, BigDecimal observed) {
        int compared = observed.compareTo(rule.getThresholdValue());
        return "ABOVE".equals(rule.getDirection()) ? compared >= 0 : compared <= 0;
    }

    private void markChecked(TradingAlertRule rule, BigDecimal observed, String status) {
        rule.setLastCheckedAt(now());
        rule.setLastObservedValue(observed);
        rule.setStatus(status);
        rule.setUpdatedAt(now());
        rules.updateById(rule);
    }

    private boolean activeToday(TradingAlertRule rule) {
        LocalDate today = LocalDate.now();
        return (rule.getValidFrom() == null || !today.isBefore(rule.getValidFrom()))
                && (rule.getValidTo() == null || !today.isAfter(rule.getValidTo()));
    }

    private NotificationChannel preferredChannel(Long userId) {
        return channels.selectList(new LambdaQueryWrapper<NotificationChannel>()
                        .eq(NotificationChannel::getUserId, userId)
                        .eq(NotificationChannel::getEnabled, true))
                .stream().min(channelPreference()).orElse(null);
    }

    private Map<Long, NotificationChannel> preferredChannels() {
        return channels.selectList(new LambdaQueryWrapper<NotificationChannel>()
                        .eq(NotificationChannel::getEnabled, true))
                .stream().collect(Collectors.groupingBy(NotificationChannel::getUserId,
                        Collectors.collectingAndThen(Collectors.minBy(channelPreference()), optional -> optional.orElse(null))));
    }

    private Comparator<NotificationChannel> channelPreference() {
        return Comparator.comparing((NotificationChannel channel) ->
                        "SERVER_CHAN".equals(channel.getChannelType()) ? 0 : 1)
                .thenComparing(NotificationChannel::getId);
    }

    private TradingAlertRule requireRule(Long userId, Long id) {
        TradingAlertRule rule = rules.selectById(id);
        if (rule == null || !userId.equals(rule.getUserId())) throw new BusinessException(ErrorCode.NOT_FOUND, "Trading alert rule not found");
        return rule;
    }

    private StockWatchItem requireWatch(Long userId, Long id) {
        StockWatchItem watch = watches.selectById(id);
        if (watch == null || !userId.equals(watch.getUserId())) throw new BusinessException(ErrorCode.NOT_FOUND, "Stock watch item not found");
        return watch;
    }

    private TradingNextPlan requirePlan(Long userId, Long id) {
        TradingNextPlan plan = plans.selectById(id);
        if (plan == null || !userId.equals(plan.getUserId())) throw new BusinessException(ErrorCode.NOT_FOUND, "Trading plan not found");
        return plan;
    }

    private String stockCode(TradingAlertRuleRequest request, StockWatchItem watch) {
        String value = watch == null ? request.stockCode() : watch.getStockCode();
        if (!StringUtils.hasText(value)) throw validation("stockCode is required when watchItemId is empty");
        return value.trim().toUpperCase();
    }

    private String stockName(TradingAlertRuleRequest request, StockWatchItem watch) {
        String value = watch == null ? request.stockName() : watch.getStockName();
        if (!StringUtils.hasText(value)) throw validation("stockName is required when watchItemId is empty");
        return value.trim();
    }

    private String title(TradingAlertRuleRequest request, String type, String direction, String stockName, BigDecimal threshold) {
        if (StringUtils.hasText(request.title())) return request.title().trim();
        return stockName + " " + type + " " + direction + " " + threshold.stripTrailingZeros().toPlainString();
    }

    private String content(TradingAlertRule rule, StockWatchItem watch, BigDecimal observed) {
        return "%s %s triggered. observed=%s, threshold=%s, latestPrice=%s, changePercent=%s%%. %s".formatted(
                watch.getStockName(),
                rule.getRuleType(),
                observed.stripTrailingZeros().toPlainString(),
                rule.getThresholdValue().stripTrailingZeros().toPlainString(),
                watch.getLatestPrice() == null ? "-" : watch.getLatestPrice().stripTrailingZeros().toPlainString(),
                watch.getChangePercent() == null ? "-" : watch.getChangePercent().stripTrailingZeros().toPlainString(),
                rule.getNote() == null ? "" : rule.getNote());
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
