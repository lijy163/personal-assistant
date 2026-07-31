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
        TradingDailyReviewMapper reviews=mock(TradingDailyReviewMapper.class);
        TradingMarketSnapshotPointMapper points=mock(TradingMarketSnapshotPointMapper.class);
        TradingNextPlanMapper plans=mock(TradingNextPlanMapper.class);TradingLogMapper trades=mock(TradingLogMapper.class);
        TradingDailyReview current=review(LocalDate.of(2026,7,31),60,"{\"limitUpStocks\":[{\"code\":\"000001\",\"streak\":2},{\"code\":\"000002\",\"streak\":3}]}");
        TradingDailyReview previous=review(LocalDate.of(2026,7,30),50,"{\"limitUpStocks\":[{\"code\":\"000001\",\"streak\":1},{\"code\":\"000003\",\"streak\":1},{\"code\":\"000002\",\"streak\":2}]}");
        when(reviews.selectList(any())).thenReturn(List.of(current,previous));
        TradingMarketSnapshotPoint point=new TradingMarketSnapshotPoint();point.setQuoteTime(LocalDateTime.of(2026,7,31,10,0));point.setSentimentScore(BigDecimal.valueOf(55));
        when(points.selectList(any())).thenReturn(List.of(point));
        TradingNextPlan done=new TradingNextPlan();done.setStatus("COMPLETED");TradingNextPlan draft=new TradingNextPlan();draft.setStatus("DRAFT");when(plans.selectList(any())).thenReturn(List.of(done,draft));
        TradingLog planned=new TradingLog();planned.setPlanned(true);TradingLog outside=new TradingLog();outside.setPlanned(false);when(trades.selectList(any())).thenReturn(List.of(planned,outside));
        var result=new TradingReviewAnalyticsService(reviews,points,plans,trades,new ObjectMapper()).analytics(7L,LocalDate.of(2026,7,31));
        assertEquals(LocalDate.of(2026,7,30),result.fiveDayTrend().get(0).tradeDate());assertEquals(1,result.intradayTimeline().size());
        assertEquals(new BigDecimal("50.00"),result.advancement().firstToSecondRate());assertEquals(new BigDecimal("100.00"),result.advancement().secondToThirdRate());
        assertEquals(new BigDecimal("50.00"),result.execution().planCompletionRate());assertEquals(new BigDecimal("50.00"),result.execution().plannedTradeRate());
    }
    private TradingDailyReview review(LocalDate date,int score,String raw){TradingDailyReview value=new TradingDailyReview();value.setTradeDate(date);value.setSentimentScore(BigDecimal.valueOf(score));value.setRawMetrics(raw);return value;}
}
