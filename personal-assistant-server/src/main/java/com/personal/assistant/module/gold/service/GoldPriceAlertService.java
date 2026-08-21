package com.personal.assistant.module.gold.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.gold.dto.GoldPriceAlertRuleRequest;
import com.personal.assistant.module.gold.dto.GoldPriceAlertRuleResponse;
import com.personal.assistant.module.gold.dto.GoldPublicQuoteResponse;
import com.personal.assistant.module.gold.entity.GoldPriceAlertRule;
import com.personal.assistant.module.gold.entity.GoldPriceAlertState;
import com.personal.assistant.module.gold.mapper.GoldPriceAlertRuleMapper;
import com.personal.assistant.module.gold.mapper.GoldPriceAlertStateMapper;
import com.personal.assistant.module.reminder.entity.NotificationChannel;
import com.personal.assistant.module.reminder.mapper.NotificationChannelMapper;
import com.personal.assistant.module.reminder.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GoldPriceAlertService {
    private static final Logger log = LoggerFactory.getLogger(GoldPriceAlertService.class);
    private static final Set<String> QUOTE_TYPES = Set.of("MARKET");

    private final PublicGoldQuoteService quotes;
    private final NotificationChannelMapper channels;
    private final GoldPriceAlertRuleMapper rules;
    private final GoldPriceAlertStateMapper states;
    private final NotificationService notifications;

    public GoldPriceAlertService(PublicGoldQuoteService quotes, NotificationChannelMapper channels,
                                 GoldPriceAlertRuleMapper rules, GoldPriceAlertStateMapper states,
                                 NotificationService notifications) {
        this.quotes = quotes;
        this.channels = channels;
        this.rules = rules;
        this.states = states;
        this.notifications = notifications;
    }

    public List<GoldPriceAlertRuleResponse> listRules(Long userId) {
        Map<String, GoldPriceAlertState> stateByKey = states.selectList(new LambdaQueryWrapper<GoldPriceAlertState>()
                        .eq(GoldPriceAlertState::getUserId, userId))
                .stream().collect(Collectors.toMap(GoldPriceAlertState::getAlertKey, state -> state));
        boolean channelConfigured = channels.selectCount(new LambdaQueryWrapper<NotificationChannel>()
                .eq(NotificationChannel::getUserId, userId)
                .eq(NotificationChannel::getEnabled, true)) > 0;
        return rules.selectList(new LambdaQueryWrapper<GoldPriceAlertRule>()
                        .eq(GoldPriceAlertRule::getUserId, userId)
                        .eq(GoldPriceAlertRule::getQuoteType, "MARKET")
                        .orderByAsc(GoldPriceAlertRule::getId))
                .stream().map(rule -> response(rule, stateByKey.get(rule.getAlertKey()), channelConfigured)).toList();
    }

    @Transactional
    public Long saveRule(Long userId, Long id, GoldPriceAlertRuleRequest request) {
        validate(request);
        GoldPriceAlertRule rule = id == null ? new GoldPriceAlertRule() : requireRule(userId, id);
        rule.setUserId(userId);
        if (id == null) {
            rule.setAlertKey("RULE_" + UUID.randomUUID().toString().replace("-", ""));
            rule.setCreatedAt(LocalDateTime.now());
        }
        rule.setTitle(request.title().trim());
        rule.setQuoteType(request.quoteType().trim().toUpperCase());
        rule.setThreshold(request.threshold());
        rule.setBrandNames(null);
        rule.setEnabled(!Boolean.FALSE.equals(request.enabled()));
        rule.setUpdatedAt(LocalDateTime.now());
        if (id == null) rules.insert(rule); else rules.updateById(rule);
        resetState(userId, rule.getAlertKey());
        return rule.getId();
    }

    @Transactional
    public void toggleRule(Long userId, Long id, boolean enabled) {
        GoldPriceAlertRule rule = requireRule(userId, id);
        rule.setEnabled(enabled);
        rule.setUpdatedAt(LocalDateTime.now());
        rules.updateById(rule);
        if (enabled) resetState(userId, rule.getAlertKey());
    }

    @Transactional
    public void deleteRule(Long userId, Long id) {
        GoldPriceAlertRule rule = requireRule(userId, id);
        resetState(userId, rule.getAlertKey());
        rules.deleteById(rule.getId());
    }

    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Shanghai")
    public void scan() {
        try {
            GoldPublicQuoteResponse response = quotes.latest();
            preferredChannels().forEach((userId, channel) -> scanUserSafely(userId, channel, response));
        } catch (RuntimeException exception) {
            log.warn("金价阈值提醒扫描失败", exception);
        }
    }

    void scanUser(Long userId, NotificationChannel channel, GoldPublicQuoteResponse response) {
        rules.selectList(new LambdaQueryWrapper<GoldPriceAlertRule>()
                        .eq(GoldPriceAlertRule::getUserId, userId)
                        .eq(GoldPriceAlertRule::getEnabled, true)
                        .eq(GoldPriceAlertRule::getQuoteType, "MARKET")
                        .orderByAsc(GoldPriceAlertRule::getId))
                .forEach(rule -> evaluateRule(userId, channel, rule, response));
    }

    private void evaluateRule(Long userId, NotificationChannel channel, GoldPriceAlertRule rule,
                              GoldPublicQuoteResponse response) {
        List<GoldPublicQuoteResponse.Quote> matched = response.quotes().stream()
                .filter(quote -> matches(rule, quote))
                .toList();
        if (matched.isEmpty()) return;
        BigDecimal currentPrice = matched.stream().map(GoldPublicQuoteResponse.Quote::price)
                .min(BigDecimal::compareTo).orElseThrow();
        String affected = matched.stream()
                .filter(quote -> quote.price().compareTo(rule.getThreshold()) < 0)
                .map(quote -> label(quote) + " " + price(quote.price()) + " 元/克")
                .collect(Collectors.joining("、"));
        String content = affected.isEmpty() ? "金价已回升至阈值以上。"
                : affected + "，已跌破 " + price(rule.getThreshold()) + " 元/克。";
        evaluate(userId, channel, rule.getAlertKey(), currentPrice, rule.getThreshold(), rule.getTitle(), content);
    }

    @Transactional
    protected void evaluate(Long userId, NotificationChannel channel, String alertKey, BigDecimal currentPrice,
                            BigDecimal threshold, String title, String content) {
        GoldPriceAlertState state = states.selectOne(new LambdaQueryWrapper<GoldPriceAlertState>()
                .eq(GoldPriceAlertState::getUserId, userId)
                .eq(GoldPriceAlertState::getAlertKey, alertKey));
        boolean below = currentPrice.compareTo(threshold) < 0;
        boolean wasBelow = state != null && Boolean.TRUE.equals(state.getBelowThreshold());
        if (below && !wasBelow) notifications.send(userId, null, channel.getId(), title, content);
        if (state == null) {
            state = new GoldPriceAlertState();
            state.setUserId(userId);
            state.setAlertKey(alertKey);
            state.setBelowThreshold(below);
            state.setLastPrice(currentPrice);
            state.setLastNotifiedAt(below ? LocalDateTime.now() : null);
            state.setUpdatedAt(LocalDateTime.now());
            states.insert(state);
        } else {
            state.setBelowThreshold(below);
            state.setLastPrice(currentPrice);
            if (below && !wasBelow) state.setLastNotifiedAt(LocalDateTime.now());
            state.setUpdatedAt(LocalDateTime.now());
            states.updateById(state);
        }
    }

    private GoldPriceAlertRuleResponse response(GoldPriceAlertRule rule, GoldPriceAlertState state,
                                                boolean channelConfigured) {
        String status = !Boolean.TRUE.equals(rule.getEnabled()) ? "DISABLED"
                : !channelConfigured ? "NO_CHANNEL"
                : state != null && Boolean.TRUE.equals(state.getBelowThreshold()) ? "TRIGGERED" : "MONITORING";
        String condition = "实时折算金价 < " + price(rule.getThreshold()) + " 元/克";
        return new GoldPriceAlertRuleResponse(rule.getId(), rule.getAlertKey(), rule.getTitle(), rule.getQuoteType(),
                rule.getThreshold(), rule.getBrandNames(), Boolean.TRUE.equals(rule.getEnabled()), condition, status,
                state == null ? null : state.getLastPrice(), state == null ? null : state.getLastNotifiedAt(),
                channelConfigured, "每 5 分钟");
    }

    private boolean matches(GoldPriceAlertRule rule, GoldPublicQuoteResponse.Quote quote) {
        return "MARKET".equals(rule.getQuoteType()) && "XAU_CNY_GRAM".equals(quote.code());
    }

    private String label(GoldPublicQuoteResponse.Quote quote) {
        return "实时折算金价";
    }

    private void validate(GoldPriceAlertRuleRequest request) {
        String type = request.quoteType().trim().toUpperCase();
        if (!QUOTE_TYPES.contains(type)) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的金价类型");
    }

    private GoldPriceAlertRule requireRule(Long userId, Long id) {
        GoldPriceAlertRule rule = rules.selectById(id);
        if (rule == null || !userId.equals(rule.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "金价提醒规则不存在");
        }
        return rule;
    }

    private void resetState(Long userId, String alertKey) {
        states.delete(new LambdaQueryWrapper<GoldPriceAlertState>()
                .eq(GoldPriceAlertState::getUserId, userId)
                .eq(GoldPriceAlertState::getAlertKey, alertKey));
    }

    private void scanUserSafely(Long userId, NotificationChannel channel, GoldPublicQuoteResponse response) {
        try {
            scanUser(userId, channel, response);
        } catch (RuntimeException exception) {
            log.warn("用户 {} 的金价阈值提醒处理失败", userId, exception);
        }
    }

    private Map<Long, NotificationChannel> preferredChannels() {
        return channels.selectList(new LambdaQueryWrapper<NotificationChannel>()
                        .eq(NotificationChannel::getEnabled, true))
                .stream().collect(Collectors.groupingBy(NotificationChannel::getUserId, LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.minBy(channelPreference()), java.util.Optional::orElseThrow)));
    }

    private Comparator<NotificationChannel> channelPreference() {
        return Comparator.comparing((NotificationChannel channel) ->
                        "SERVER_CHAN".equals(channel.getChannelType()) ? 0 : 1)
                .thenComparing(NotificationChannel::getId);
    }

    private String price(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
