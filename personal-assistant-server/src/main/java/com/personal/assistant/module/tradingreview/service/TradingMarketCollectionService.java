package com.personal.assistant.module.tradingreview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.module.tradingreview.dto.CollectionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personal.assistant.module.tradingreview.dto.MarketSnapshot;
import com.personal.assistant.module.tradingreview.dto.SentimentResult;
import com.personal.assistant.module.tradingreview.entity.TradingDailyReview;
import com.personal.assistant.module.tradingreview.mapper.TradingDailyReviewMapper;
import com.personal.assistant.module.tradingreview.mapper.TradingMarketSnapshotPointMapper;
import com.personal.assistant.module.tradingreview.entity.TradingMarketSnapshotPoint;
import com.personal.assistant.module.tradingreview.provider.TradingMarketDataProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class TradingMarketCollectionService {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private final TradingDailyReviewMapper mapper;
    private final TradingMarketDataProvider provider;
    private final SentimentRuleEngine ruleEngine;
    private final TradingCalendarService calendar;
    private final TradingMarketSnapshotPointMapper points;
    private final ObjectMapper objectMapper;
    private final TradingMarketAlertService alertService;

    public TradingMarketCollectionService(TradingDailyReviewMapper mapper, TradingMarketDataProvider provider,
                                          SentimentRuleEngine ruleEngine, TradingCalendarService calendar,
                                          TradingMarketSnapshotPointMapper points, ObjectMapper objectMapper,
                                          TradingMarketAlertService alertService) {
        this.mapper = mapper;
        this.provider = provider;
        this.ruleEngine = ruleEngine;
        this.calendar = calendar;
        this.points = points;
        this.objectMapper = objectMapper;
        this.alertService = alertService;
    }

    @Transactional
    public CollectionResponse refresh(Long userId, LocalDate requestedDate, String requestedType) {
        LocalDateTime now = LocalDateTime.now(SHANGHAI);
        LocalDate tradeDate = requestedDate == null ? now.toLocalDate() : requestedDate;
        String snapshotType = normalizeType(requestedType, now);
        TradingDailyReview review = find(userId, tradeDate, snapshotType);
        if (review == null) review = newReview(userId, tradeDate, snapshotType, now);
        try {
            MarketSnapshot snapshot = withTurnoverChange(userId, tradeDate, snapshotType, provider.fetch(tradeDate));
            snapshot = withIntradayBenchmark(userId, tradeDate, snapshotType, snapshot);
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
            savePoint(userId, tradeDate, snapshotType, snapshot, result, now);
            alertService.scanReview(userId, review);
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

    private MarketSnapshot withTurnoverChange(Long userId, LocalDate tradeDate, String snapshotType,
                                              MarketSnapshot snapshot) {
        if (snapshot.turnoverChange() != null || !"FINAL".equals(snapshotType) || snapshot.turnoverAmount() == null)
            return snapshot;
        TradingDailyReview previous = mapper.selectOne(new LambdaQueryWrapper<TradingDailyReview>()
                .eq(TradingDailyReview::getUserId, userId)
                .eq(TradingDailyReview::getSnapshotType, "FINAL")
                .lt(TradingDailyReview::getTradeDate, tradeDate)
                .isNotNull(TradingDailyReview::getTurnoverAmount)
                .orderByDesc(TradingDailyReview::getTradeDate).last("limit 1"));
        if (previous == null || previous.getTurnoverAmount() == null || previous.getTurnoverAmount().signum() == 0)
            return snapshot;
        BigDecimal change = snapshot.turnoverAmount().subtract(previous.getTurnoverAmount())
                .multiply(BigDecimal.valueOf(100)).divide(previous.getTurnoverAmount(), 2, RoundingMode.HALF_UP);
        return new MarketSnapshot(snapshot.shanghaiChange(), snapshot.shenzhenChange(), snapshot.chinextChange(),
                snapshot.risingCount(), snapshot.fallingCount(), snapshot.flatCount(), snapshot.limitUpCount(),
                snapshot.limitDownCount(), snapshot.brokenBoardCount(), snapshot.brokenBoardRate(), snapshot.maxStreak(),
                snapshot.turnoverAmount(), change, snapshot.industrySectors(), snapshot.conceptSectors(), snapshot.source(),
                snapshot.quoteTime(), snapshot.rawMetrics());
    }

    private MarketSnapshot withIntradayBenchmark(Long userId, LocalDate tradeDate, String snapshotType,
                                                  MarketSnapshot snapshot) {
        if (!"REALTIME".equals(snapshotType) || snapshot.turnoverAmount() == null || snapshot.quoteTime() == null)
            return snapshot;
        List<TradingMarketSnapshotPoint> history = points.selectList(new LambdaQueryWrapper<TradingMarketSnapshotPoint>()
                .eq(TradingMarketSnapshotPoint::getUserId, userId).eq(TradingMarketSnapshotPoint::getSnapshotType, "REALTIME")
                .lt(TradingMarketSnapshotPoint::getTradeDate, tradeDate).isNotNull(TradingMarketSnapshotPoint::getTurnoverAmount)
                .orderByDesc(TradingMarketSnapshotPoint::getTradeDate).last("limit 30"));
        LocalTime nowTime = snapshot.quoteTime().toLocalTime();
        List<BigDecimal> comparable = history.stream().filter(point -> point.getQuoteTime() != null
                        && Math.abs(java.time.Duration.between(point.getQuoteTime().toLocalTime(), nowTime).toMinutes()) <= 10)
                .map(TradingMarketSnapshotPoint::getTurnoverAmount).filter(value -> value.signum() > 0).limit(5).toList();
        BigDecimal benchmark = comparable.isEmpty() ? null : comparable.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(comparable.size()), 2, RoundingMode.HALF_UP);
        BigDecimal change = benchmark == null ? null : snapshot.turnoverAmount().subtract(benchmark)
                .multiply(BigDecimal.valueOf(100)).divide(benchmark, 2, RoundingMode.HALF_UP);
        String raw = addIntradayBenchmark(snapshot.rawMetrics(), benchmark, comparable.size(), change);
        return new MarketSnapshot(snapshot.shanghaiChange(), snapshot.shenzhenChange(), snapshot.chinextChange(),
                snapshot.risingCount(), snapshot.fallingCount(), snapshot.flatCount(), snapshot.limitUpCount(),
                snapshot.limitDownCount(), snapshot.brokenBoardCount(), snapshot.brokenBoardRate(), snapshot.maxStreak(),
                snapshot.turnoverAmount(), change, snapshot.industrySectors(), snapshot.conceptSectors(), snapshot.source(),
                snapshot.quoteTime(), raw);
    }

    private String addIntradayBenchmark(String rawMetrics, BigDecimal benchmark, int sampleCount, BigDecimal change) {
        try {
            ObjectNode raw = rawMetrics == null || rawMetrics.isBlank() ? objectMapper.createObjectNode()
                    : (ObjectNode) objectMapper.readTree(rawMetrics);
            raw.putObject("intradayBenchmark").put("sampleCount", sampleCount).put("turnoverAmount", benchmark)
                    .put("turnoverChange", change).put("status", sampleCount >= 3 ? "AVAILABLE" : "INSUFFICIENT_HISTORY");
            JsonNode quality = raw.path("dataQuality");
            if (quality instanceof ObjectNode qualityNode) qualityNode.put("intradayBenchmarkSamples", sampleCount);
            return raw.toString();
        } catch (Exception exception) { return rawMetrics; }
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

    private void savePoint(Long uid, LocalDate date, String type, MarketSnapshot snapshot, SentimentResult result, LocalDateTime now) {
        TradingMarketSnapshotPoint point=new TradingMarketSnapshotPoint();point.setUserId(uid);point.setTradeDate(date);point.setSnapshotType(type);
        point.setQuoteTime(snapshot.quoteTime());point.setSentimentScore(result.score());point.setMarketStage(result.stage());
        point.setRisingCount(snapshot.risingCount());point.setFallingCount(snapshot.fallingCount());point.setLimitUpCount(snapshot.limitUpCount());
        point.setLimitDownCount(snapshot.limitDownCount());point.setBrokenBoardRate(snapshot.brokenBoardRate());point.setMaxStreak(snapshot.maxStreak());
        point.setTurnoverAmount(snapshot.turnoverAmount());point.setRawMetrics(snapshot.rawMetrics());point.setCreatedAt(now);points.insert(point);
    }

    private void save(TradingDailyReview review) {
        if (review.getId() == null) mapper.insert(review); else mapper.updateById(review);
    }

    private String normalizeType(String requested, LocalDateTime now) {
        if (requested != null && (requested.equalsIgnoreCase("REALTIME") || requested.equalsIgnoreCase("FINAL"))) return requested.toUpperCase();
        return now.toLocalTime().isBefore(LocalTime.of(15, 10)) ? "REALTIME" : "FINAL";
    }
}
