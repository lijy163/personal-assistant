package com.personal.assistant.module.gold.service;

import com.personal.assistant.module.gold.dto.GoldPriceAlertRuleRequest;
import com.personal.assistant.module.gold.dto.GoldPublicQuoteResponse;
import com.personal.assistant.module.gold.entity.GoldPriceAlertRule;
import com.personal.assistant.module.gold.entity.GoldPriceAlertState;
import com.personal.assistant.module.gold.mapper.GoldPriceAlertRuleMapper;
import com.personal.assistant.module.gold.mapper.GoldPriceAlertStateMapper;
import com.personal.assistant.module.reminder.entity.NotificationChannel;
import com.personal.assistant.module.reminder.mapper.NotificationChannelMapper;
import com.personal.assistant.module.reminder.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoldPriceAlertServiceTest {
    @Mock private PublicGoldQuoteService quotes;
    @Mock private NotificationChannelMapper channels;
    @Mock private GoldPriceAlertRuleMapper rules;
    @Mock private GoldPriceAlertStateMapper states;
    @Mock private NotificationService notifications;

    private GoldPriceAlertService service;
    private NotificationChannel channel;

    @BeforeEach
    void setUp() {
        service = new GoldPriceAlertService(quotes, channels, rules, states, notifications);
        channel = new NotificationChannel();
        channel.setId(7L);
        channel.setUserId(3L);
        channel.setEnabled(true);
    }

    @Test
    void sendsMarketAlertFromDatabaseRule() {
        when(rules.selectList(any())).thenReturn(List.of(rule("MARKET_800", "MARKET", "800", null)));
        when(states.selectOne(any())).thenReturn(null);

        service.scanUser(3L, channel, response(quote("XAU_CNY_GRAM", "799")));

        verify(notifications).send(eq(3L), eq(null), eq(7L), any(), any());
        verify(states).insert(any(GoldPriceAlertState.class));
    }

    @Test
    void doesNotRepeatUntilPriceRecoversAndCrossesAgain() {
        GoldPriceAlertState triggered = state(true);
        when(states.selectOne(any())).thenReturn(null, triggered, triggered, state(false));

        service.evaluate(3L, channel, "MARKET_800", decimal("799"), decimal("800"), "提醒", "内容");
        service.evaluate(3L, channel, "MARKET_800", decimal("790"), decimal("800"), "提醒", "内容");
        service.evaluate(3L, channel, "MARKET_800", decimal("805"), decimal("800"), "提醒", "内容");
        service.evaluate(3L, channel, "MARKET_800", decimal("795"), decimal("800"), "提醒", "内容");

        verify(notifications, times(2)).send(3L, null, 7L, "提醒", "内容");
    }

    @Test
    void rejectsJewelryAlertRule() {
        assertThrows(RuntimeException.class, () -> service.saveRule(3L, null,
                new GoldPriceAlertRuleRequest("首饰金跌破 950", "JEWELRY", decimal("950"), "周大福", true)));
    }

    @Test
    void savesEditableRuleAndResetsPreviousState() {
        GoldPriceAlertRule existing = rule("MARKET_800", "MARKET", "800", null);
        existing.setId(9L);
        when(rules.selectById(9L)).thenReturn(existing);

        Long id = service.saveRule(3L, 9L,
                new GoldPriceAlertRuleRequest("实时金价跌破 750", "MARKET", decimal("750"), null, true));

        assertEquals(9L, id);
        assertEquals(decimal("750"), existing.getThreshold());
        assertEquals(null, existing.getBrandNames());
        verify(rules).updateById(existing);
        verify(states).delete(any());
    }

    private GoldPriceAlertRule rule(String key, String type, String threshold, String brands) {
        GoldPriceAlertRule rule = new GoldPriceAlertRule();
        rule.setUserId(3L);
        rule.setAlertKey(key);
        rule.setTitle(key);
        rule.setQuoteType(type);
        rule.setThreshold(decimal(threshold));
        rule.setBrandNames(brands);
        rule.setEnabled(true);
        return rule;
    }

    private GoldPriceAlertState state(boolean below) {
        GoldPriceAlertState state = new GoldPriceAlertState();
        state.setId(1L);
        state.setUserId(3L);
        state.setAlertKey("MARKET_800");
        state.setBelowThreshold(below);
        return state;
    }

    private GoldPublicQuoteResponse response(GoldPublicQuoteResponse.Quote... quotes) {
        LocalDateTime now = LocalDateTime.now();
        return new GoldPublicQuoteResponse(List.of(quotes), decimal("7.2"), now, now, "test", 60);
    }

    private GoldPublicQuoteResponse.Quote quote(String code, String price) {
        return new GoldPublicQuoteResponse.Quote(code, code, decimal(price), "元/克", "test", false);
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
