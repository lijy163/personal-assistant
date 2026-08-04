package com.personal.assistant.module.tradingreview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.assistant.module.tradingreview.entity.*;
import com.personal.assistant.module.tradingreview.mapper.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TradingReviewAnalyticsServiceTest {
    @Test
    void calculatesAdvancementTimelineTrendAndExecutionRates() {
        TradingDailyReviewMapper reviews = mock(TradingDailyReviewMapper.class);
        TradingMarketSnapshotPointMapper points = mock(TradingMarketSnapshotPointMapper.class);
        TradingNextPlanMapper plans = mock(TradingNextPlanMapper.class);
        TradingLogMapper trades = mock(TradingLogMapper.class);
        TradingExecutionMapper executions = mock(TradingExecutionMapper.class);
        TradingAlertEventMapper alertEvents = mock(TradingAlertEventMapper.class);
        TradingMarketAlertEventMapper marketAlertEvents = mock(TradingMarketAlertEventMapper.class);
        TradingDailyReview current = review(LocalDate.of(2026, 7, 31), 60, "{\"limitUpStocks\":[{\"code\":\"000001\",\"streak\":2},{\"code\":\"000002\",\"streak\":3}]}");
        TradingDailyReview previous = review(LocalDate.of(2026, 7, 30), 50, "{\"limitUpStocks\":[{\"code\":\"000001\",\"streak\":1},{\"code\":\"000003\",\"streak\":1},{\"code\":\"000002\",\"streak\":2}]}");
        when(reviews.selectList(any())).thenReturn(List.of(current, previous));
        TradingMarketSnapshotPoint point = new TradingMarketSnapshotPoint();
        point.setQuoteTime(LocalDateTime.of(2026, 7, 31, 10, 0));
        point.setSentimentScore(BigDecimal.valueOf(55));
        when(points.selectList(any())).thenReturn(List.of(point));
        TradingNextPlan done = new TradingNextPlan(); done.setStatus("COMPLETED"); done.setTargetPosition(new BigDecimal("40")); done.setReportNote("实际仓位 50");
        TradingNextPlan draft = new TradingNextPlan(); draft.setStatus("DRAFT");
        when(plans.selectList(any())).thenReturn(List.of(done, draft));
        TradingLog planned = new TradingLog(); planned.setPlanned(true);
        TradingLog outside = new TradingLog(); outside.setPlanned(false);
        when(trades.selectList(any())).thenReturn(List.of(planned, outside));
        TradingAlertEvent alert = new TradingAlertEvent(); alert.setTriggeredAt(LocalDateTime.of(2026, 7, 31, 10, 0));
        when(alertEvents.selectList(any())).thenReturn(List.of(alert));
        TradingExecution execution = new TradingExecution(); execution.setOccurredAt(LocalDateTime.of(2026, 7, 31, 10, 30));
        when(executions.selectList(any())).thenReturn(List.of(execution));
        when(marketAlertEvents.selectCount(any())).thenReturn(1L);

        var result = new TradingReviewAnalyticsService(reviews, points, plans, trades, executions, alertEvents,
                marketAlertEvents, new ObjectMapper()).analytics(7L, LocalDate.of(2026, 7, 31));

        assertEquals(LocalDate.of(2026, 7, 30), result.fiveDayTrend().get(0).tradeDate());
        assertEquals(1, result.intradayTimeline().size());
        assertEquals(new BigDecimal("50.00"), result.advancement().firstToSecondRate());
        assertEquals(new BigDecimal("100.00"), result.advancement().secondToThirdRate());
        assertEquals(new BigDecimal("50.00"), result.execution().planCompletionRate());
        assertEquals(new BigDecimal("50.00"), result.execution().plannedTradeRate());
        assertEquals(new BigDecimal("50.00"), result.execution().alertTradeRate());
        assertEquals(new BigDecimal("30.00"), result.execution().averageAlertResponseMinutes());
        assertEquals(new BigDecimal("10.00"), result.execution().averagePositionDeviation());
        assertEquals("TRACKED", result.execution().status());
    }

    private TradingDailyReview review(LocalDate date, int score, String raw) {
        TradingDailyReview value = new TradingDailyReview();
        value.setTradeDate(date);
        value.setSentimentScore(BigDecimal.valueOf(score));
        value.setRawMetrics(raw);
        return value;
    }
}