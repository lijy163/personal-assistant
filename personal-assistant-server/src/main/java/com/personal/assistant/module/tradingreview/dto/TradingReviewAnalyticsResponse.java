package com.personal.assistant.module.tradingreview.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
public record TradingReviewAnalyticsResponse(
        List<DailyTrend> fiveDayTrend, List<TimelinePoint> intradayTimeline,
        Advancement advancement, Execution execution) {
    public record DailyTrend(LocalDate tradeDate, BigDecimal sentimentScore, Integer risingCount, Integer fallingCount,
                             Integer limitUpCount, Integer limitDownCount, BigDecimal brokenBoardRate,
                             Integer maxStreak, BigDecimal turnoverAmount, BigDecimal turnoverChange) {}
    public record TimelinePoint(LocalDateTime quoteTime, BigDecimal sentimentScore, String marketStage,
                                Integer risingCount, Integer fallingCount, Integer limitUpCount,
                                Integer limitDownCount, BigDecimal brokenBoardRate, BigDecimal turnoverAmount) {}
    public record Advancement(LocalDate currentDate, LocalDate previousDate, Integer previousFirstBoards,
                              Integer firstToSecond, BigDecimal firstToSecondRate, Integer previousSecondBoards,
                              Integer secondToThird, BigDecimal secondToThirdRate, String status) {}
    public record Execution(long duePlans, long completedPlans, BigDecimal planCompletionRate,
                            long trades, long plannedTrades, BigDecimal plannedTradeRate) {}
}
