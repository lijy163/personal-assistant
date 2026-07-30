package com.personal.assistant.module.tradingreview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.module.tradingreview.dto.CollectionResponse;
import com.personal.assistant.module.tradingreview.dto.MarketSnapshot;
import com.personal.assistant.module.tradingreview.dto.SentimentResult;
import com.personal.assistant.module.tradingreview.entity.TradingDailyReview;
import com.personal.assistant.module.tradingreview.mapper.TradingDailyReviewMapper;
import com.personal.assistant.module.tradingreview.provider.TradingMarketDataProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

@Service
public class TradingMarketCollectionService {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private final TradingDailyReviewMapper mapper;
    private final TradingMarketDataProvider provider;
    private final SentimentRuleEngine ruleEngine;
    private final TradingCalendarService calendar;

    public TradingMarketCollectionService(TradingDailyReviewMapper mapper, TradingMarketDataProvider provider,
                                          SentimentRuleEngine ruleEngine, TradingCalendarService calendar) {
        this.mapper = mapper;
        this.provider = provider;
        this.ruleEngine = ruleEngine;
        this.calendar = calendar;
    }

    @Transactional
    public CollectionResponse refresh(Long userId, LocalDate requestedDate, String requestedType) {
        LocalDateTime now = LocalDateTime.now(SHANGHAI);
        LocalDate tradeDate = requestedDate == null ? now.toLocalDate() : requestedDate;
        String snapshotType = normalizeType(requestedType, now);
        TradingDailyReview review = find(userId, tradeDate, snapshotType);
        if (review == null) review = newReview(userId, tradeDate, snapshotType, now);
        try {
            MarketSnapshot snapshot = provider.fetch(tradeDate);
            applySnapshot(review, snapshot);
            SentimentResult result = ruleEngine.evaluate(snapshot);
            review.setSentimentScore(result.score());
            review.setMarketStage(result.stage());
            review.setSuggestedPosition(result.suggestedPosition());
            review.setAutoConclusion(result.conclusion());
            review.setRuleVersion(result.ruleVersion());
            review.setDimensionScores(result.dimensionScores());
            review.setConclusionGeneratedAt(now);
            review.setDataCompleteness(result.completeness());
            review.setCollectionStatus("SUCCESS");
            review.setFailureReason(null);
            review.setLastSuccessAt(now);
            review.setFreshness("FRESH");
            review.setUpdatedAt(now);
            save(review);
            return new CollectionResponse(review, true, result.completeness().equals("COMPLETE") ? "行情刷新成功" : "行情已刷新，部分指标需手工补充", now);
        } catch (RuntimeException exception) {
            review.setCollectionStatus("FAILED");
            review.setFailureReason(exception.getMessage());
            review.setFreshness(review.getLastSuccessAt() == null ? "NO_DATA" : "STALE");
            review.setUpdatedAt(now);
            save(review);
            return new CollectionResponse(review, false, "采集失败，已保留旧数据：" + exception.getMessage(), review.getLastSuccessAt());
        }
    }

    private void applySnapshot(TradingDailyReview review, MarketSnapshot value) {
        review.setShanghaiChange(value.shanghaiChange());
        review.setShenzhenChange(value.shenzhenChange());
        review.setChinextChange(value.chinextChange());
        review.setRisingCount(value.risingCount());
        review.setFallingCount(value.fallingCount());
        review.setFlatCount(value.flatCount());
        review.setLimitUpCount(value.limitUpCount());
        review.setLimitDownCount(value.limitDownCount());
        review.setBrokenBoardCount(value.brokenBoardCount());
        review.setBrokenBoardRate(value.brokenBoardRate());
        review.setMaxStreak(value.maxStreak());
        review.setTurnoverAmount(value.turnoverAmount());
        review.setTurnoverChange(value.turnoverChange());
        review.setSectors("行业板块：" + value.industrySectors() + "\n概念板块：" + value.conceptSectors());
        review.setDataSource(value.source());
        review.setQuoteTime(value.quoteTime());
        review.setCollectedAt(LocalDateTime.now(SHANGHAI));
        review.setRawMetrics(value.rawMetrics());
    }

    private TradingDailyReview find(Long userId, LocalDate date, String type) {
        return mapper.selectOne(new LambdaQueryWrapper<TradingDailyReview>()
                .eq(TradingDailyReview::getUserId, userId)
                .eq(TradingDailyReview::getTradeDate, date)
                .eq(TradingDailyReview::getSnapshotType, type));
    }

    private TradingDailyReview newReview(Long userId, LocalDate date, String type, LocalDateTime now) {
        TradingDailyReview review = new TradingDailyReview();
        review.setUserId(userId);
        review.setTradeDate(date);
        review.setSnapshotType(type);
        review.setTradingDay(calendar.isTradingDay(date));
        review.setStatus("DRAFT");
        review.setDataCompleteness("INCOMPLETE");
        review.setCollectionStatus("PENDING");
        review.setCreatedAt(now);
        review.setUpdatedAt(now);
        return review;
    }

    private void save(TradingDailyReview review) {
        if (review.getId() == null) mapper.insert(review); else mapper.updateById(review);
    }

    private String normalizeType(String requested, LocalDateTime now) {
        if (requested != null && (requested.equalsIgnoreCase("REALTIME") || requested.equalsIgnoreCase("FINAL"))) return requested.toUpperCase();
        return now.toLocalTime().isBefore(LocalTime.of(15, 10)) ? "REALTIME" : "FINAL";
    }
}
