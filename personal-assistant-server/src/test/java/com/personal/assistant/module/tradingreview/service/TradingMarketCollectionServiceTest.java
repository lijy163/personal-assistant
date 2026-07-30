package com.personal.assistant.module.tradingreview.service;

import com.personal.assistant.module.tradingreview.entity.TradingDailyReview;
import com.personal.assistant.module.tradingreview.mapper.TradingDailyReviewMapper;
import com.personal.assistant.module.tradingreview.provider.TradingMarketDataProvider;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TradingMarketCollectionServiceTest {
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
                new TradingCalendarService()).refresh(7L, LocalDate.of(2026,7,30), "FINAL");

        assertFalse(result.fresh());
        assertEquals(3000, result.review().getRisingCount());
        assertEquals("人工判断保留", result.review().getManualJudgment());
        assertEquals("FAILED", result.review().getCollectionStatus());
        assertEquals("STALE", result.review().getFreshness());
        verify(mapper).updateById(old);
    }
}
