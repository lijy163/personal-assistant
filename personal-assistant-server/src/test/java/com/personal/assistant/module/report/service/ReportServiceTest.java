package com.personal.assistant.module.report.service;

import com.personal.assistant.module.report.dto.ReportStats;
import com.personal.assistant.module.report.entity.GeneratedReport;
import com.personal.assistant.module.report.mapper.GeneratedReportMapper;
import com.personal.assistant.module.report.mapper.ReportStatsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {
    @Mock
    GeneratedReportMapper reports;
    @Mock
    ReportStatsMapper stats;
    ReportService service;

    @BeforeEach
    void setUp() {
        service = new ReportService(reports, stats);
        when(stats.stats(any(), any(), any())).thenReturn(
                new ReportStats(3, 5, 120, 2, BigDecimal.valueOf(1000), BigDecimal.valueOf(300)));
    }

    @Test
    void weeklyReportUsesMondayThroughSunday() {
        GeneratedReport report = service.generate(7L, "WEEKLY", LocalDate.of(2026, 7, 29));

        assertEquals(LocalDate.of(2026, 7, 27), report.getPeriodStart());
        assertEquals(LocalDate.of(2026, 8, 2), report.getPeriodEnd());
        assertTrue(report.getMarkdownContent().contains("结余：¥700"));
        verify(reports).insert(report);
    }

    @Test
    void monthlyReportUpdatesExistingPeriod() {
        GeneratedReport existing = new GeneratedReport();
        existing.setId(10L);
        when(reports.selectOne(any())).thenReturn(existing);

        GeneratedReport report = service.generate(7L, "MONTHLY", LocalDate.of(2026, 2, 12));

        assertEquals(LocalDate.of(2026, 2, 1), report.getPeriodStart());
        assertEquals(LocalDate.of(2026, 2, 28), report.getPeriodEnd());
        verify(reports).updateById(existing);
        verify(reports, never()).insert(any(GeneratedReport.class));
    }
}
