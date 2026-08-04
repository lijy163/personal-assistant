package com.personal.assistant.module.tradingreview.service;

import com.personal.assistant.module.tradingreview.dto.MarketSnapshot;
import com.personal.assistant.module.tradingreview.dto.SentimentResult;
import com.personal.assistant.module.tradingreview.entity.TradingDailyReview;
import java.math.BigDecimal;
import com.personal.assistant.module.tradingreview.mapper.TradingDailyReviewMapper;
import com.personal.assistant.module.tradingreview.provider.TradingMarketDataProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TradingMarketCollectionServiceTest {
    @Test
    void finalSnapshotCalculatesTurnoverChangeFromPreviousFinalReview() {
        TradingDailyReviewMapper mapper = mock(TradingDailyReviewMapper.class);
        TradingMarketDataProvider provider = mock(TradingMarketDataProvider.class);
        SentimentRuleEngine rules = mock(SentimentRuleEngine.class);
        TradingDailyReview previous = new TradingDailyReview();
        previous.setTurnoverAmount(new BigDecimal("1000"));
        MarketSnapshot snapshot = new MarketSnapshot(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                3000, 1500, 100, 80, 5, 10, new BigDecimal("12"), 6,
                new BigDecimal("1200"), null, "行业", "概念", "TEST", LocalDateTime.now(), "{}");
        when(mapper.selectOne(any())).thenReturn(null, previous);
        when(provider.fetch(any())).thenReturn(snapshot);
        when(rules.evaluate(any())).thenReturn(new SentimentResult(new BigDecimal("60"), "震荡",
                new BigDecimal("40"), "结论", "V2.0", "{}", "COMPLETE"));

        var result = new TradingMarketCollectionService(mapper, provider, rules,
                new TradingCalendarService(), mock(com.personal.assistant.module.tradingreview.mapper.TradingMarketSnapshotPointMapper.class), new ObjectMapper(), mock(TradingMarketAlertService.class)).refresh(7L, LocalDate.of(2026, 7, 30), "FINAL");

        assertTrue(result.fresh());
        assertEquals(new BigDecimal("20.00"), result.review().getTurnoverChange());
        verify(mapper).insert(result.review());
    }

    @Test
    void collectionFailureKeepsOldDataAndManualJudgment() {
        TradingDailyReviewMapper mapper = mock(TradingDailyReviewMapper.class);
        TradingMarketDataProvider provider = mock(TradingMarketDataProvider.class);
        TradingDailyReview old = new TradingDailyReview(); old.setId(9L); old.setUserId(7L);
        old.setTradeDate(LocalDate.of(2026,7,30)); old.setSnapshotType("FINAL");
        old.setRisingCount(3000); old.setManualJudgment("人工判断保留");
        old.setLastSuccessAt(LocalDateTime.of(2026,7,29,15,10));
        when(mapper.selectOne(any())).thenReturn(old);
        when(provider.fetch(any())).thenThrow(new RuntimeException("network down"));

        var result = new TradingMarketCollectionService(mapper, provider, mock(SentimentRuleEngine.class),
                new TradingCalendarService(), mock(com.personal.assistant.module.tradingreview.mapper.TradingMarketSnapshotPointMapper.class), new ObjectMapper(), mock(TradingMarketAlertService.class)).refresh(7L, LocalDate.of(2026,7,30), "FINAL");

        assertFalse(result.fresh());
        assertEquals(3000, result.review().getRisingCount());
        assertEquals("人工判断保留", result.review().getManualJudgment());
        assertEquals("FAILED", result.review().getCollectionStatus());
        assertEquals("STALE", result.review().getFreshness());
        verify(mapper).updateById(old);
    }
}
