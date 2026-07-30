package com.personal.assistant.module.gold.service;

import com.personal.assistant.module.gold.dto.GoldPublicQuoteResponse;
import com.personal.assistant.module.gold.entity.GoldPriceAlertState;
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
    @Mock private GoldPriceAlertStateMapper states;
    @Mock private NotificationService notifications;

    private GoldPriceAlertService service;
    private NotificationChannel channel;

    @BeforeEach
    void setUp() {
        service = new GoldPriceAlertService(quotes, channels, states, notifications);
        channel = new NotificationChannel();
        channel.setId(7L);
        channel.setUserId(3L);
        channel.setEnabled(true);
    }

    @Test
    void sendsThreeIndependentAlertsWhenAllThresholdsAreCrossed() {
        when(states.selectOne(any())).thenReturn(null);

        service.scanUser(3L, channel, response(
                quote("XAU_CNY_GRAM", "799"),
                quote("JEWELRY_周大福", "899"),
                quote("JEWELRY_老庙", "920")
        ));

        verify(notifications, times(3)).send(eq(3L), eq(null), eq(7L), any(), any());
        verify(states, times(3)).insert(any(GoldPriceAlertState.class));
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
    void ignoresJewelryBrandsNotShownOnDashboard() {
        when(states.selectOne(any())).thenReturn(null);

        service.scanUser(3L, channel, response(
                quote("XAU_CNY_GRAM", "810"),
                quote("JEWELRY_君佩", "850")
        ));

        verify(notifications, never()).send(any(), any(), any(), any(), any());
        verify(states, times(1)).insert(any(GoldPriceAlertState.class));
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
        return new GoldPublicQuoteResponse(List.of(quotes), decimal("7.2"), now, now, "test", 60, true, true, "ok");
    }

    private GoldPublicQuoteResponse.Quote quote(String code, String price) {
        return new GoldPublicQuoteResponse.Quote(code, code, decimal(price), "元/克", "test", false);
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
