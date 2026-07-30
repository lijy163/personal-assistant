package com.personal.assistant.module.tradingreview.service;

import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.module.tradingreview.entity.TradingExecution;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TradeMetricsCalculatorTest {
    private final TradeMetricsCalculator calculator = new TradeMetricsCalculator();

    @Test
    void calculatesPartialBuysAndSellsWithFees() {
        List<TradingExecution> rows = List.of(
                execution("BUY", "100", "10", "5", "0", "0", 1), execution("BUY", "100", "12", "5", "0", "0", 2),
                execution("SELL", "50", "14", "5", "1", "0", 3), execution("SELL", "50", "9", "5", "1", "0", 4));
        var metrics = calculator.calculate(rows, new BigDecimal("13"));
        assertEquals(0, new BigDecimal("200").compareTo(metrics.buyQuantity()));
        assertEquals(0, new BigDecimal("100").compareTo(metrics.remainingQuantity()));
        assertEquals(0, new BigDecimal("11.0500").compareTo(metrics.averageCost()));
        assertEquals(0, new BigDecimal("33.0000").compareTo(metrics.realizedProfit()));
        assertEquals(0, new BigDecimal("195.0000").compareTo(metrics.unrealizedProfit()));
        assertEquals(0, new BigDecimal("22.0000").compareTo(metrics.totalFees()));
    }

    @Test
    void rejectsSellingMoreThanPosition() {
        assertThrows(BusinessException.class, () -> calculator.calculate(List.of(
                execution("BUY", "100", "10", "0", "0", "0", 1), execution("SELL", "101", "11", "0", "0", "0", 2)), null));
    }

    private TradingExecution execution(String side, String quantity, String price, String commission, String stampDuty, String transferFee, int day) {
        TradingExecution value = new TradingExecution(); value.setSide(side); value.setQuantity(new BigDecimal(quantity)); value.setPrice(new BigDecimal(price));
        value.setCommission(new BigDecimal(commission)); value.setStampDuty(new BigDecimal(stampDuty)); value.setTransferFee(new BigDecimal(transferFee));
        value.setOccurredAt(LocalDateTime.of(2026, 7, day, 10, 0)); return value;
    }
}
