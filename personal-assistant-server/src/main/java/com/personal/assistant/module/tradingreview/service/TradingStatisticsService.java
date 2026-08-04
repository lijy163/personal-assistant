package com.personal.assistant.module.tradingreview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.module.tradingreview.dto.TradeDetailResponse;
import com.personal.assistant.module.tradingreview.dto.TradingStatsResponse;
import com.personal.assistant.module.tradingreview.entity.TradingMistake;
import com.personal.assistant.module.tradingreview.mapper.TradingMistakeMapper;
import com.personal.assistant.module.tradingreview.provider.TradePriceRangeProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TradingStatisticsService {
    private final TradingReviewService reviewService;
    private final TradingMistakeMapper mistakeMapper;
    private final TradePriceRangeProvider priceRanges;

    public TradingStatisticsService(TradingReviewService reviewService, TradingMistakeMapper mistakeMapper, TradePriceRangeProvider priceRanges) {
        this.reviewService = reviewService;
        this.mistakeMapper = mistakeMapper;
        this.priceRanges = priceRanges;
    }

    public TradingStatsResponse calculate(Long userId) {
        List<TradeDetailResponse> all = reviewService.trades(userId).stream()
                .map(trade -> reviewService.trade(userId, trade.getId())).toList();
        List<TradeDetailResponse> closed = all.stream()
                .filter(trade -> trade.metrics().buyQuantity().signum() > 0 && trade.metrics().remainingQuantity().signum() == 0)
                .toList();
        List<BigDecimal> profits = closed.stream().map(trade -> trade.metrics().realizedProfit()).toList();
        long wins = profits.stream().filter(value -> value.signum() > 0).count();
        long losses = profits.stream().filter(value -> value.signum() < 0).count();
        BigDecimal averageWin = average(profits.stream().filter(value -> value.signum() > 0).toList());
        BigDecimal averageLoss = average(profits.stream().filter(value -> value.signum() < 0).map(BigDecimal::abs).toList());
        BigDecimal ratio = averageLoss.signum() == 0 ? BigDecimal.ZERO : averageWin.divide(averageLoss, 4, RoundingMode.HALF_UP);
        BigDecimal averageReturn = average(closed.stream().map(trade -> trade.metrics().returnRate()).toList());
        BigDecimal realized = sum(all.stream().map(trade -> trade.metrics().realizedProfit()).toList());
        BigDecimal unrealized = sum(all.stream().map(trade -> trade.metrics().unrealizedProfit()).toList());
        BigDecimal fees = sum(all.stream().map(trade -> trade.metrics().totalFees()).toList());
        BigDecimal averageMfe = average(all.stream().map(this::mfe).toList());
        BigDecimal averageMae = average(all.stream().map(this::mae).toList());
        BigDecimal maxDrawdown = maxDrawdown(closed);
        List<TradingStatsResponse.Attribution> strategy = attribution(all, trade -> blank(trade.trade().getStrategy(), "?????"));
        List<TradingStatsResponse.Attribution> signal = attribution(all, trade -> blank(trade.trade().getSignalType(), "?????"));
        List<TradingStatsResponse.Attribution> holding = attribution(all, trade -> holdingBucket(trade.metrics().holdingDays()));
        Map<String, Long> frequency = mistakeMapper.selectList(new LambdaQueryWrapper<TradingMistake>()
                        .eq(TradingMistake::getUserId, userId)).stream()
                .collect(Collectors.groupingBy(TradingMistake::getCategory,
                        Collectors.summingLong(value -> Math.max(1, value.getRepeatCount()))));
        List<TradingStatsResponse.ErrorFrequency> errors = frequency.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(10)
                .map(value -> new TradingStatsResponse.ErrorFrequency(value.getKey(), value.getValue())).toList();
        BigDecimal winRate = rate(wins, closed.size());
        return new TradingStatsResponse(closed.size(), wins, losses, winRate, averageReturn, averageWin, averageLoss,
                ratio, realized, unrealized, fees, averageMfe, averageMae, maxDrawdown, strategy, signal, holding, errors);
    }

    private BigDecimal mfe(TradeDetailResponse trade) {
        BigDecimal avg = trade.metrics().averageCost();
        if (avg == null || avg.signum() == 0) return BigDecimal.ZERO;
        return priceRanges.range(trade.trade().getStockCode(), openDate(trade), closeDate(trade))
                .map(range -> range.highestPrice().subtract(avg).max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(100)).divide(avg, 2, RoundingMode.HALF_UP))
                .orElseGet(() -> fallbackExcursion(trade, true));
    }

    private BigDecimal mae(TradeDetailResponse trade) {
        BigDecimal avg = trade.metrics().averageCost();
        if (avg == null || avg.signum() == 0) return BigDecimal.ZERO;
        return priceRanges.range(trade.trade().getStockCode(), openDate(trade), closeDate(trade))
                .map(range -> avg.subtract(range.lowestPrice()).max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(100)).divide(avg, 2, RoundingMode.HALF_UP))
                .orElseGet(() -> fallbackExcursion(trade, false));
    }

    private BigDecimal fallbackExcursion(TradeDetailResponse trade, boolean favorable) {
        BigDecimal invested = trade.executions().stream().filter(v -> "BUY".equals(v.getSide()))
                .map(v -> v.getQuantity().multiply(v.getPrice())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal profit = trade.metrics().realizedProfit().add(trade.metrics().unrealizedProfit());
        BigDecimal value = favorable ? profit.max(BigDecimal.ZERO) : profit.min(BigDecimal.ZERO).abs();
        return invested.signum() == 0 ? BigDecimal.ZERO : value.multiply(BigDecimal.valueOf(100)).divide(invested, 2, RoundingMode.HALF_UP);
    }

    private java.time.LocalDate openDate(TradeDetailResponse trade) {
        return trade.executions().stream().map(v -> v.getOccurredAt()).filter(Objects::nonNull).min(java.time.LocalDateTime::compareTo)
                .map(java.time.LocalDateTime::toLocalDate).orElse(java.time.LocalDate.now());
    }

    private java.time.LocalDate closeDate(TradeDetailResponse trade) {
        return trade.trade().getClosedAt() == null ? java.time.LocalDate.now() : trade.trade().getClosedAt().toLocalDate();
    }

    private BigDecimal maxDrawdown(List<TradeDetailResponse> closed) {
        BigDecimal equity = BigDecimal.ZERO, peak = BigDecimal.ZERO, worst = BigDecimal.ZERO;
        for (TradeDetailResponse trade : closed.stream().sorted(Comparator.comparing(v -> v.trade().getClosedAt(), Comparator.nullsLast(Comparator.naturalOrder()))).toList()) {
            equity = equity.add(trade.metrics().realizedProfit());
            if (equity.compareTo(peak) > 0) peak = equity;
            BigDecimal drawdown = peak.subtract(equity);
            if (drawdown.compareTo(worst) > 0) worst = drawdown;
        }
        return worst.setScale(4, RoundingMode.HALF_UP);
    }

    private List<TradingStatsResponse.Attribution> attribution(List<TradeDetailResponse> trades, java.util.function.Function<TradeDetailResponse, String> classifier) {
        return trades.stream().collect(Collectors.groupingBy(classifier)).entrySet().stream()
                .map(entry -> {
                    List<TradeDetailResponse> rows = entry.getValue();
                    long wins = rows.stream().filter(v -> v.metrics().realizedProfit().add(v.metrics().unrealizedProfit()).signum() > 0).count();
                    return new TradingStatsResponse.Attribution(entry.getKey(), rows.size(),
                            sum(rows.stream().map(v -> v.metrics().realizedProfit()).toList()),
                            average(rows.stream().map(v -> v.metrics().returnRate()).toList()), rate(wins, rows.size()));
                }).sorted((a, b) -> b.realizedProfit().compareTo(a.realizedProfit())).limit(8).toList();
    }

    private String holdingBucket(Integer days) {
        int value = days == null ? 0 : days;
        if (value <= 1) return "1??";
        if (value <= 3) return "2-3?";
        if (value <= 7) return "4-7?";
        return "7???";
    }

    private String blank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private BigDecimal average(List<BigDecimal> values) { return values.isEmpty() ? BigDecimal.ZERO : sum(values).divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP); }
    private BigDecimal sum(List<BigDecimal> values) { return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add); }
    private BigDecimal rate(long value, long total) { return total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(value * 100).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP); }
}
