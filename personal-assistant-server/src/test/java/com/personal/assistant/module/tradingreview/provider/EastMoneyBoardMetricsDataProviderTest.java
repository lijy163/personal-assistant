package com.personal.assistant.module.tradingreview.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class EastMoneyBoardMetricsDataProviderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EastMoneyBoardMetricsDataProvider provider = new EastMoneyBoardMetricsDataProvider(
            mock(EastMoneyTradingMarketDataProvider.class), objectMapper);

    @Test
    void parsesLimitUpBrokenBoardAndHighestStreak() throws Exception {
        var limitUp = objectMapper.readTree("""
                {"data":{"tc":52,"pool":[
                  {"lbc":1,"zttj":{"ct":1}},
                  {"lbc":8,"zttj":{"ct":8}},
                  {"zttj":{"ct":3}}
                ]}}
                """);
        var brokenBoard = objectMapper.readTree("""
                {"data":{"tc":19,"pool":[{}]}}
                """);

        var result = provider.parseBoardMetrics(limitUp, brokenBoard);

        assertEquals(52, result.limitUpCount());
        assertEquals(19, result.brokenBoardCount());
        assertEquals(new BigDecimal("26.76"), result.brokenBoardRate());
        assertEquals(8, result.maxStreak());
    }

    @Test
    void zeroTouchedLimitCountProducesZeroRate() throws Exception {
        var emptyPool = objectMapper.readTree("""
                {"data":{"tc":0,"pool":[]}}
                """);

        var result = provider.parseBoardMetrics(emptyPool, emptyPool);

        assertEquals(BigDecimal.ZERO, result.brokenBoardRate());
        assertEquals(0, result.maxStreak());
    }

    @Test
    void rejectsMissingPoolDataInsteadOfTreatingItAsZero() throws Exception {
        var missing = objectMapper.readTree("{\"data\":null}");
        var valid = objectMapper.readTree("{\"data\":{\"tc\":0,\"pool\":[]}}");

        assertThrows(IllegalStateException.class, () -> provider.parseBoardMetrics(missing, valid));
    }
}
