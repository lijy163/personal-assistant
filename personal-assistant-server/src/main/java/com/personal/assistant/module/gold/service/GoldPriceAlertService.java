package com.personal.assistant.module.gold.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.module.gold.dto.GoldPublicQuoteResponse;
import com.personal.assistant.module.gold.entity.GoldPriceAlertState;
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
import java.util.stream.Collectors;

@Service
public class GoldPriceAlertService {
    private static final Logger log = LoggerFactory.getLogger(GoldPriceAlertService.class);
    private static final Set<String> DASHBOARD_JEWELRY_BRANDS = Set.of("周大福", "老庙");
    private static final BigDecimal MARKET_THRESHOLD = new BigDecimal("800");
    private static final List<BigDecimal> JEWELRY_THRESHOLDS = List.of(new BigDecimal("1000"), new BigDecimal("900"));

    private final PublicGoldQuoteService quotes;
    private final NotificationChannelMapper channels;
    private final GoldPriceAlertStateMapper states;
    private final NotificationService notifications;

    public GoldPriceAlertService(PublicGoldQuoteService quotes, NotificationChannelMapper channels,
                                 GoldPriceAlertStateMapper states, NotificationService notifications) {
        this.quotes = quotes;
        this.channels = channels;
        this.states = states;
        this.notifications = notifications;
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
        response.quotes().stream()
                .filter(quote -> "XAU_CNY_GRAM".equals(quote.code()))
                .findFirst()
                .ifPresent(quote -> evaluate(userId, channel, "MARKET_800", quote.price(), MARKET_THRESHOLD,
                        "实时金价跌破 800 元/克",
                        "当前实时折算金价为 " + price(quote.price()) + " 元/克，已跌破 800 元/克。"));

        List<GoldPublicQuoteResponse.Quote> jewelryQuotes = response.quotes().stream()
                .filter(this::isDashboardJewelry)
                .toList();
        if (jewelryQuotes.isEmpty()) return;
        BigDecimal lowestPrice = jewelryQuotes.stream().map(GoldPublicQuoteResponse.Quote::price)
                .min(BigDecimal::compareTo).orElseThrow();
        for (BigDecimal threshold : JEWELRY_THRESHOLDS) {
            String affected = jewelryQuotes.stream()
                    .filter(quote -> quote.price().compareTo(threshold) < 0)
                    .map(quote -> brand(quote.code()) + " " + price(quote.price()) + " 元/克")
                    .collect(Collectors.joining("、"));
            evaluate(userId, channel, "JEWELRY_" + threshold.toPlainString(), lowestPrice, threshold,
                    "首饰金价跌破 " + threshold.toPlainString() + " 元/克",
                    affected.isEmpty() ? "首饰金价已回升。" : affected + "，已跌破 " + threshold.toPlainString() + " 元/克。");
        }
    }

    @Transactional
    protected void evaluate(Long userId, NotificationChannel channel, String alertKey, BigDecimal currentPrice,
                            BigDecimal threshold, String title, String content) {
        GoldPriceAlertState state = states.selectOne(new LambdaQueryWrapper<GoldPriceAlertState>()
                .eq(GoldPriceAlertState::getUserId, userId)
                .eq(GoldPriceAlertState::getAlertKey, alertKey));
        boolean below = currentPrice.compareTo(threshold) < 0;
        boolean wasBelow = state != null && Boolean.TRUE.equals(state.getBelowThreshold());
        if (below && !wasBelow) {
            notifications.send(userId, null, channel.getId(), title, content);
        }
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
                .stream()
                .collect(Collectors.groupingBy(NotificationChannel::getUserId, LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.minBy(channelPreference()), java.util.Optional::orElseThrow)));
    }

    private Comparator<NotificationChannel> channelPreference() {
        return Comparator.comparing((NotificationChannel channel) ->
                        "SERVER_CHAN".equals(channel.getChannelType()) ? 0 : 1)
                .thenComparing(NotificationChannel::getId);
    }

    private boolean isDashboardJewelry(GoldPublicQuoteResponse.Quote quote) {
        return quote.code().startsWith("JEWELRY_") && DASHBOARD_JEWELRY_BRANDS.contains(brand(quote.code()));
    }

    private String brand(String code) {
        return code.substring("JEWELRY_".length());
    }

    private String price(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
