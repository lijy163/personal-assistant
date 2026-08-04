package com.personal.assistant.module.tradingreview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.assistant.module.tradingreview.dto.TradingReviewAnalyticsResponse;
import com.personal.assistant.module.tradingreview.entity.*;
import com.personal.assistant.module.tradingreview.mapper.*;
import org.springframework.stereotype.Service;

import java.math.*;
import java.time.*;
import java.util.*;

@Service
public class TradingReviewAnalyticsService {
    private final TradingDailyReviewMapper reviews;
    private final TradingMarketSnapshotPointMapper points;
    private final TradingNextPlanMapper plans;
    private final TradingLogMapper trades;
    private final TradingExecutionMapper executions;
    private final TradingAlertEventMapper alertEvents;
    private final TradingMarketAlertEventMapper marketAlertEvents;
    private final ObjectMapper json;

    public TradingReviewAnalyticsService(TradingDailyReviewMapper reviews, TradingMarketSnapshotPointMapper points,
                                         TradingNextPlanMapper plans, TradingLogMapper trades,
                                         TradingExecutionMapper executions, TradingAlertEventMapper alertEvents,
                                         TradingMarketAlertEventMapper marketAlertEvents, ObjectMapper json) {
        this.reviews = reviews;
        this.points = points;
        this.plans = plans;
        this.trades = trades;
        this.executions = executions;
        this.alertEvents = alertEvents;
        this.marketAlertEvents = marketAlertEvents;
        this.json = json;
    }

    public TradingReviewAnalyticsResponse analytics(Long uid, LocalDate date) {
        LocalDate target = date == null ? LocalDate.now(ZoneId.of("Asia/Shanghai")) : date;
        List<TradingDailyReview> finals = reviews.selectList(new LambdaQueryWrapper<TradingDailyReview>()
                .eq(TradingDailyReview::getUserId, uid).eq(TradingDailyReview::getSnapshotType, "FINAL")
                .le(TradingDailyReview::getTradeDate, target).orderByDesc(TradingDailyReview::getTradeDate).last("limit 5"));
        List<TradingReviewAnalyticsResponse.DailyTrend> trend = new ArrayList<>();
        List<TradingDailyReview> chronological = new ArrayList<>(finals);
        Collections.reverse(chronological);
        chronological.forEach(v -> trend.add(new TradingReviewAnalyticsResponse.DailyTrend(v.getTradeDate(), v.getSentimentScore(),
                v.getRisingCount(), v.getFallingCount(), v.getLimitUpCount(), v.getLimitDownCount(), v.getBrokenBoardRate(),
                v.getMaxStreak(), v.getTurnoverAmount(), v.getTurnoverChange())));
        List<TradingReviewAnalyticsResponse.TimelinePoint> timeline = points.selectList(new LambdaQueryWrapper<TradingMarketSnapshotPoint>()
                .eq(TradingMarketSnapshotPoint::getUserId, uid).eq(TradingMarketSnapshotPoint::getTradeDate, target)
                .orderByAsc(TradingMarketSnapshotPoint::getQuoteTime)).stream().map(v -> new TradingReviewAnalyticsResponse.TimelinePoint(
                v.getQuoteTime(), v.getSentimentScore(), v.getMarketStage(), v.getRisingCount(), v.getFallingCount(),
                v.getLimitUpCount(), v.getLimitDownCount(), v.getBrokenBoardRate(), v.getTurnoverAmount())).toList();
        return new TradingReviewAnalyticsResponse(trend, timeline, advancement(finals), execution(uid, target));
    }

    private TradingReviewAnalyticsResponse.Advancement advancement(List<TradingDailyReview> finals) {
        if (finals.size() < 2) return unavailable(finals.isEmpty() ? null : finals.get(0).getTradeDate(), null, "历史收盘数据不足");
        TradingDailyReview current = finals.get(0), previous = finals.get(1);
        Map<String, Integer> now = stocks(current.getRawMetrics()), before = stocks(previous.getRawMetrics());
        if (now.isEmpty() || before.isEmpty()) return unavailable(current.getTradeDate(), previous.getTradeDate(), "涨停股票明细尚未积累");
        long first = before.values().stream().filter(v -> v == 1).count(), second = before.values().stream().filter(v -> v == 2).count();
        long firstAdvanced = before.entrySet().stream().filter(v -> v.getValue() == 1 && now.getOrDefault(v.getKey(), 0) >= 2).count();
        long secondAdvanced = before.entrySet().stream().filter(v -> v.getValue() == 2 && now.getOrDefault(v.getKey(), 0) >= 3).count();
        return new TradingReviewAnalyticsResponse.Advancement(current.getTradeDate(), previous.getTradeDate(), (int) first,
                (int) firstAdvanced, rate(firstAdvanced, first), (int) second, (int) secondAdvanced, rate(secondAdvanced, second), "AVAILABLE");
    }

    private Map<String, Integer> stocks(String raw) {
        Map<String, Integer> result = new HashMap<>();
        if (raw == null) return result;
        try {
            for (JsonNode v : json.readTree(raw).path("limitUpStocks")) result.put(v.path("code").asText(), v.path("streak").asInt(1));
        } catch (Exception ignored) {
        }
        return result;
    }

    private TradingReviewAnalyticsResponse.Advancement unavailable(LocalDate now, LocalDate before, String status) {
        return new TradingReviewAnalyticsResponse.Advancement(now, before, 0, 0, BigDecimal.ZERO, 0, 0, BigDecimal.ZERO, status);
    }

    private TradingReviewAnalyticsResponse.Execution execution(Long uid, LocalDate date) {
        List<TradingNextPlan> due = plans.selectList(new LambdaQueryWrapper<TradingNextPlan>()
                .eq(TradingNextPlan::getUserId, uid).le(TradingNextPlan::getTradeDate, date).ne(TradingNextPlan::getStatus, "CANCELLED"));
        List<TradingLog> logs = trades.selectList(new LambdaQueryWrapper<TradingLog>().eq(TradingLog::getUserId, uid));
        long completed = due.stream().filter(v -> "COMPLETED".equals(v.getStatus())).count();
        long planned = logs.stream().filter(v -> Boolean.TRUE.equals(v.getPlanned())).count();
        LocalDateTime start = date.atStartOfDay(), end = date.plusDays(1).atStartOfDay();
        List<TradingAlertEvent> stockAlerts = alertEvents.selectList(new LambdaQueryWrapper<TradingAlertEvent>()
                .eq(TradingAlertEvent::getUserId, uid).ge(TradingAlertEvent::getTriggeredAt, start).lt(TradingAlertEvent::getTriggeredAt, end));
        long marketAlerts = marketAlertEvents.selectCount(new LambdaQueryWrapper<TradingMarketAlertEvent>()
                .eq(TradingMarketAlertEvent::getUserId, uid).eq(TradingMarketAlertEvent::getTradeDate, date));
        List<TradingExecution> dayExecutions = executions.selectList(new LambdaQueryWrapper<TradingExecution>()
                .eq(TradingExecution::getUserId, uid).ge(TradingExecution::getOccurredAt, start).lt(TradingExecution::getOccurredAt, end));
        Map<Long, TradingLog> tradeById = tradesByExecution(dayExecutions);
        long linked = linkedTrades(stockAlerts, dayExecutions, tradeById);
        BigDecimal avgResponse = averageResponseMinutes(stockAlerts, dayExecutions, tradeById);
        BigDecimal avgDeviation = averagePositionDeviation(due, logs);
        long allAlerts = stockAlerts.size() + marketAlerts;
        String status = allAlerts == 0 ? "NO_ALERT" : linked == 0 ? "NO_RESPONSE" : "TRACKED";
        return new TradingReviewAnalyticsResponse.Execution(due.size(), completed, rate(completed, due.size()), logs.size(), planned,
                rate(planned, logs.size()), allAlerts, linked, rate(linked, allAlerts), avgResponse, avgDeviation, status);
    }

    private Map<Long, TradingLog> tradesByExecution(List<TradingExecution> dayExecutions) {
        Map<Long, TradingLog> result = new HashMap<>();
        for (TradingExecution execution : dayExecutions) {
            if (execution.getTradeLogId() == null || result.containsKey(execution.getTradeLogId())) continue;
            TradingLog trade = trades.selectById(execution.getTradeLogId());
            if (trade != null) result.put(execution.getTradeLogId(), trade);
        }
        return result;
    }

    private long linkedTrades(List<TradingAlertEvent> alerts, List<TradingExecution> dayExecutions, Map<Long, TradingLog> tradeById) {
        return alerts.stream().filter(alert -> dayExecutions.stream().anyMatch(execution -> matches(alert, execution, tradeById))).count();
    }

    private BigDecimal averageResponseMinutes(List<TradingAlertEvent> alerts, List<TradingExecution> dayExecutions, Map<Long, TradingLog> tradeById) {
        List<BigDecimal> minutes = new ArrayList<>();
        for (TradingAlertEvent alert : alerts) {
            dayExecutions.stream().filter(execution -> matches(alert, execution, tradeById)).map(TradingExecution::getOccurredAt)
                    .filter(time -> time != null && !time.isBefore(alert.getTriggeredAt())).min(LocalDateTime::compareTo)
                    .ifPresent(time -> minutes.add(BigDecimal.valueOf(Duration.between(alert.getTriggeredAt(), time).toMinutes())));
        }
        if (minutes.isEmpty()) return BigDecimal.ZERO;
        return minutes.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(minutes.size()), 2, RoundingMode.HALF_UP);
    }

    private boolean matches(TradingAlertEvent alert, TradingExecution execution, Map<Long, TradingLog> tradeById) {
        TradingLog trade = tradeById.get(execution.getTradeLogId());
        if (trade != null && alert.getId() != null && alert.getId().equals(trade.getSourceAlertEventId())) return true;
        return alert.getTriggeredAt() != null && execution.getOccurredAt() != null
                && !execution.getOccurredAt().isBefore(alert.getTriggeredAt())
                && execution.getOccurredAt().isBefore(alert.getTriggeredAt().plusHours(4));
    }

    private BigDecimal averagePositionDeviation(List<TradingNextPlan> due, List<TradingLog> logs) {
        List<BigDecimal> deviations = new ArrayList<>();
        for (TradingNextPlan plan : due) {
            if (plan.getTargetPosition() == null || plan.getReportNote() == null) continue;
            BigDecimal actual = extractActualPosition(plan.getReportNote());
            if (actual != null) deviations.add(actual.subtract(plan.getTargetPosition()).abs());
        }
        if (deviations.isEmpty()) {
            long unplanned = logs.stream().filter(log -> !Boolean.TRUE.equals(log.getPlanned())).count();
            return logs.isEmpty() ? BigDecimal.ZERO : rate(unplanned, logs.size());
        }
        return deviations.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(deviations.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal extractActualPosition(String note) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:actualPosition|实际仓位)[:：=]?\\s*(\\d+(?:\\.\\d+)?)").matcher(note);
        return matcher.find() ? new BigDecimal(matcher.group(1)) : null;
    }

    private BigDecimal rate(long value, long total) {
        return total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(value * 100).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}