package com.personal.assistant.module.tradingreview.service;

import com.personal.assistant.module.tradingreview.dto.MarketSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SentimentRuleEngineTest {
    private final SentimentRuleEngine engine = new SentimentRuleEngine();

    @Test
    void generatesExplainableResultForCompleteMetrics() {
        var result = engine.evaluate(snapshot(new BigDecimal("1.2"), 3500, 1200, 80, 5,
                new BigDecimal("20"), 7, new BigDecimal("1200000000000"), new BigDecimal("8")));
        assertNotNull(result.score());
        assertNotEquals("数据不完整", result.stage());
        assertEquals("COMPLETE", result.completeness());
        assertTrue(result.dimensionScores().contains("breadth"));
        assertEquals(SentimentRuleEngine.VERSION, result.ruleVersion());
    }

    @Test
    void missingKeyMetricIsIncompleteInsteadOfZero() {
        var result = engine.evaluate(snapshot(new BigDecimal("1.2"), 3500, 1200, 80, 5,
                null, 7, new BigDecimal("1200000000000"), new BigDecimal("8")));
        assertNull(result.score());
        assertEquals("数据不完整", result.stage());
        assertEquals("INCOMPLETE", result.completeness());
    }

    private MarketSnapshot snapshot(BigDecimal index, Integer rising, Integer falling, Integer up, Integer down,
                                    BigDecimal brokenRate, Integer streak, BigDecimal turnover, BigDecimal turnoverChange) {
        return new MarketSnapshot(index, index, index, rising, falling, 100, up, down, 10, brokenRate,
                streak, turnover, turnoverChange, "行业", "概念", "TEST", LocalDateTime.now(), "{}");
    }
}
