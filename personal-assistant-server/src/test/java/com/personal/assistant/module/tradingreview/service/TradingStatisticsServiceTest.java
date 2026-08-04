package com.personal.assistant.module.tradingreview.service;

import com.personal.assistant.module.tradingreview.dto.TradeDetailResponse;
import com.personal.assistant.module.tradingreview.dto.TradeMetrics;
import com.personal.assistant.module.tradingreview.entity.TradingLog;
import com.personal.assistant.module.tradingreview.mapper.TradingMistakeMapper;
import com.personal.assistant.module.tradingreview.provider.TradePriceRangeProvider;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TradingStatisticsServiceTest {
    @Test
    void calculatesWinRateAverageReturnAndProfitLossRatio() {
        TradingReviewService reviews = mock(TradingReviewService.class);
        TradingMistakeMapper mistakes = mock(TradingMistakeMapper.class);
        TradePriceRangeProvider ranges = mock(TradePriceRangeProvider.class);
        TradingLog win = trade(1L); TradingLog loss = trade(2L); TradingLog open = trade(3L);
        when(reviews.trades(7L)).thenReturn(List.of(win, loss, open));
        when(reviews.trade(7L, 1L)).thenReturn(detail(win, "200", "20", "0"));
        when(reviews.trade(7L, 2L)).thenReturn(detail(loss, "-50", "-10", "0"));
        when(reviews.trade(7L, 3L)).thenReturn(detail(open, "0", "4", "100"));
        when(mistakes.selectList(any())).thenReturn(List.of());

        var result = new TradingStatisticsService(reviews, mistakes, ranges).calculate(7L);

        assertEquals(new BigDecimal("50.00"), result.winRate());
        assertEquals(new BigDecimal("5.0000"), result.averageReturn());
        assertEquals(new BigDecimal("4.0000"), result.profitLossRatio());
        assertEquals(2, result.closedTrades());
    }

    private TradingLog trade(Long id) { TradingLog value = new TradingLog(); value.setId(id); return value; }
    private TradeDetailResponse detail(TradingLog trade, String profit, String rate, String remaining) {
        return new TradeDetailResponse(trade, List.of(), new TradeMetrics(new BigDecimal("100"),
                new BigDecimal("100").subtract(new BigDecimal(remaining)), new BigDecimal(remaining), BigDecimal.TEN,
                new BigDecimal(profit), BigDecimal.ZERO, BigDecimal.ONE, new BigDecimal(rate), 1));
    }
}
