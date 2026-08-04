package com.personal.assistant.module.report.service;

import com.personal.assistant.module.report.dto.ReportStats;
import com.personal.assistant.module.report.entity.GeneratedReport;
import com.personal.assistant.module.report.mapper.GeneratedReportMapper;
import com.personal.assistant.module.report.mapper.ReportStatsMapper;
import com.personal.assistant.module.tradingreview.entity.TradingDailyReview;
import com.personal.assistant.module.tradingreview.mapper.TradingAlertEventMapper;
import com.personal.assistant.module.tradingreview.mapper.TradingDailyReviewMapper;
import com.personal.assistant.module.tradingreview.mapper.TradingMarketAlertEventMapper;
import com.personal.assistant.module.tradingreview.mapper.TradingMarketSnapshotPointMapper;
import com.personal.assistant.module.tradingreview.mapper.TradingNextPlanMapper;
import com.personal.assistant.module.tradingreview.dto.TradingStatsResponse;
import com.personal.assistant.module.tradingreview.service.TradingStatisticsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {
    @Mock GeneratedReportMapper reports;
    @Mock ReportStatsMapper stats;
    @Mock TradingDailyReviewMapper tradingReviews;
    @Mock TradingMarketSnapshotPointMapper tradingPoints;
    @Mock TradingNextPlanMapper tradingPlans;
    @Mock TradingAlertEventMapper tradingAlertEvents;
    @Mock TradingMarketAlertEventMapper marketAlertEvents;
    @Mock TradingStatisticsService tradingStatistics;
    ReportService service;

    @BeforeEach
    void setUp() {
        service = new ReportService(reports, stats, tradingReviews, tradingPoints, tradingPlans,
                tradingAlertEvents, marketAlertEvents, tradingStatistics, new ObjectMapper());
    }

    @Test
    void weeklyReportUsesMondayThroughSunday() {
        when(stats.stats(any(), any(), any())).thenReturn(
                new ReportStats(3, 5, 120, 2, BigDecimal.valueOf(1000), BigDecimal.valueOf(300)));
        GeneratedReport report = service.generate(7L, "WEEKLY", LocalDate.of(2026, 7, 29));

        assertEquals(LocalDate.of(2026, 7, 27), report.getPeriodStart());
        assertEquals(LocalDate.of(2026, 8, 2), report.getPeriodEnd());
        assertTrue(report.getMarkdownContent().contains("结余：¥700"));
        verify(reports).insert(report);
    }

    @Test
    void monthlyReportUpdatesExistingPeriod() {
        when(stats.stats(any(), any(), any())).thenReturn(
                new ReportStats(3, 5, 120, 2, BigDecimal.valueOf(1000), BigDecimal.valueOf(300)));
        GeneratedReport existing = new GeneratedReport();
        existing.setId(10L);
        when(reports.selectOne(any())).thenReturn(existing);

        GeneratedReport report = service.generate(7L, "MONTHLY", LocalDate.of(2026, 2, 12));

        assertEquals(LocalDate.of(2026, 2, 1), report.getPeriodStart());
        assertEquals(LocalDate.of(2026, 2, 28), report.getPeriodEnd());
        verify(reports).updateById(existing);
        verify(reports, never()).insert(any(GeneratedReport.class));
    }

    @Test
    void tradingDailyReportSummarizesMarketSnapshot() {
        TradingDailyReview review = new TradingDailyReview();
        review.setId(3L);
        review.setUserId(7L);
        review.setTradeDate(LocalDate.of(2026, 8, 4));
        review.setSnapshotType("FINAL");
        review.setCollectionStatus("SUCCESS");
        review.setMarketStage("WARM");
        review.setSentimentScore(new BigDecimal("62"));
        review.setSuggestedPosition(new BigDecimal("50"));
        review.setRisingCount(3100);
        review.setFallingCount(1800);
        review.setFlatCount(120);
        review.setLimitUpCount(75);
        review.setLimitDownCount(3);
        review.setBrokenBoardRate(new BigDecimal("18.5"));
        review.setMaxStreak(5);
        review.setTurnoverAmount(new BigDecimal("920000000000"));
        review.setTurnoverChange(new BigDecimal("8.2"));
        review.setRawMetrics("{\"marketMedian\":{\"change\":0.7},\"sectorRankings\":{\"rising\":[{\"name\":\"半导体\"}],\"turnover\":[{\"name\":\"电子\"}]}} ");
        when(tradingReviews.selectOne(any())).thenReturn(review).thenReturn(null);
        when(tradingReviews.selectList(any())).thenReturn(List.of(review));
        when(tradingStatistics.calculate(7L)).thenReturn(new TradingStatsResponse(0,0,0,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,List.of(),List.of(),List.of(),List.of()));

        GeneratedReport report = service.generate(7L, "TRADING_DAILY", LocalDate.of(2026, 8, 4));

        assertEquals("TRADING_DAILY", report.getReportType());
        assertEquals(LocalDate.of(2026, 8, 4), report.getPeriodStart());
        assertTrue(report.getMarkdownContent().contains("## 强弱原因"));
        assertTrue(report.getMarkdownContent().contains("半导体"));
        verify(reports).insert(report);
    }
}
