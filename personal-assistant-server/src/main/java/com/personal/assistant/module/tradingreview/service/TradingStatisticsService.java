package com.personal.assistant.module.tradingreview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.module.tradingreview.dto.TradeDetailResponse;
import com.personal.assistant.module.tradingreview.dto.TradingStatsResponse;
import com.personal.assistant.module.tradingreview.entity.TradingMistake;
import com.personal.assistant.module.tradingreview.mapper.TradingMistakeMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TradingStatisticsService {
    private final TradingReviewService reviewService;
    private final TradingMistakeMapper mistakeMapper;

    public TradingStatisticsService(TradingReviewService reviewService, TradingMistakeMapper mistakeMapper) {
        this.reviewService = reviewService;
        this.mistakeMapper = mistakeMapper;
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
        Map<String, Long> frequency = mistakeMapper.selectList(new LambdaQueryWrapper<TradingMistake>()
                        .eq(TradingMistake::getUserId, userId)).stream()
                .collect(Collectors.groupingBy(TradingMistake::getCategory,
                        Collectors.summingLong(value -> Math.max(1, value.getRepeatCount()))));
        List<TradingStatsResponse.ErrorFrequency> errors = frequency.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(10)
                .map(value -> new TradingStatsResponse.ErrorFrequency(value.getKey(), value.getValue())).toList();
        BigDecimal winRate = closed.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(wins * 100)
                .divide(BigDecimal.valueOf(closed.size()), 2, RoundingMode.HALF_UP);
        return new TradingStatsResponse(closed.size(), wins, losses, winRate, averageReturn, averageWin, averageLoss,
                ratio, realized, unrealized, fees, errors);
    }

    private BigDecimal average(List<BigDecimal> values) {
        return values.isEmpty() ? BigDecimal.ZERO : sum(values).divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
