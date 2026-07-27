package com.personal.assistant.module.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.stock.dto.StockFundFlowOverviewResponse;
import com.personal.assistant.module.stock.dto.StockFundFlowRefreshResponse;
import com.personal.assistant.module.stock.dto.StockFundFlowStatusResponse;
import com.personal.assistant.module.stock.entity.StockFundFlowCollection;
import com.personal.assistant.module.stock.entity.StockFundFlowSnapshot;
import com.personal.assistant.module.stock.entity.StockWatchItem;
import com.personal.assistant.module.stock.mapper.StockFundFlowCollectionMapper;
import com.personal.assistant.module.stock.mapper.StockFundFlowSnapshotMapper;
import com.personal.assistant.module.stock.mapper.StockWatchItemMapper;
import com.personal.assistant.module.stock.provider.StockFundFlowPoint;
import com.personal.assistant.module.stock.provider.StockFundFlowProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StockFundFlowService {
    private static final String PERIOD_DAILY = "DAILY";
    private static final int HISTORY_LIMIT = 20;
    private final StockWatchItemMapper watches;
    private final StockFundFlowSnapshotMapper snapshots;
    private final StockFundFlowCollectionMapper collections;
    private final StockFundFlowProvider provider;

    public StockFundFlowService(StockWatchItemMapper watches, StockFundFlowSnapshotMapper snapshots,
                                StockFundFlowCollectionMapper collections, StockFundFlowProvider provider) {
        this.watches = watches; this.snapshots = snapshots; this.collections = collections; this.provider = provider;
    }

    @Transactional
    public StockFundFlowRefreshResponse refresh(Long userId) {
        return refreshItems(watches.selectList(new LambdaQueryWrapper<StockWatchItem>()
                .eq(StockWatchItem::getUserId, userId).eq(StockWatchItem::getEnabled, true)
                .eq(StockWatchItem::getMarket, "CN").orderByAsc(StockWatchItem::getStockCode)));
    }

    @Transactional
    public StockFundFlowRefreshResponse refreshForScheduler() {
        return refreshItems(watches.selectList(new LambdaQueryWrapper<StockWatchItem>()
                .eq(StockWatchItem::getEnabled, true).eq(StockWatchItem::getMarket, "CN")
                .orderByAsc(StockWatchItem::getUserId).orderByAsc(StockWatchItem::getStockCode)));
    }

    public StockFundFlowOverviewResponse overview(Long userId) {
        List<StockWatchItem> items = enabledCnWatches(userId);
        Map<Long, StockWatchItem> itemMap = items.stream().collect(Collectors.toMap(StockWatchItem::getId, Function.identity()));
        List<StockFundFlowSnapshot> latest = latestSnapshots(userId, itemMap.keySet().stream().toList());
        BigDecimal total = latest.stream().map(StockFundFlowSnapshot::getMainNetInflow).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int inflow = (int) latest.stream().filter(item -> positive(item.getMainNetInflow())).count();
        int outflow = (int) latest.stream().filter(item -> negative(item.getMainNetInflow())).count();
        LocalDateTime latestTime = latest.stream().map(StockFundFlowSnapshot::getQuoteTime).max(LocalDateTime::compareTo).orElse(null);
        List<StockFundFlowOverviewResponse.RankingItem> ranking = latest.stream().map(snapshot -> {
            StockWatchItem item = itemMap.get(snapshot.getWatchItemId());
            return new StockFundFlowOverviewResponse.RankingItem(snapshot.getWatchItemId(), snapshot.getStockCode(),
                    item == null ? snapshot.getStockCode() : item.getStockName(), snapshot.getMainNetInflow(),
                    snapshot.getMainNetRatio(), snapshot.getSuperLargeNetInflow(), snapshot.getLargeNetInflow(),
                    snapshot.getMediumNetInflow(), snapshot.getSmallNetInflow(), snapshot.getChangePercent(), snapshot.getQuoteTime());
        }).sorted((left, right) -> nullSafe(right.mainNetInflow()).compareTo(nullSafe(left.mainNetInflow()))).toList();
        return new StockFundFlowOverviewResponse(provider.name(), latestTime, items.size(), latest.size(),
                rate(latest.size(), items.size()), total, inflow, outflow, ranking);
    }

    public List<StockFundFlowSnapshot> trend(Long userId, Long watchId, int days) {
        requireWatch(userId, watchId);
        int safeDays = Math.max(1, Math.min(days, 120));
        return snapshots.selectList(new LambdaQueryWrapper<StockFundFlowSnapshot>()
                .eq(StockFundFlowSnapshot::getUserId, userId).eq(StockFundFlowSnapshot::getWatchItemId, watchId)
                .eq(StockFundFlowSnapshot::getPeriodType, PERIOD_DAILY)
                .orderByDesc(StockFundFlowSnapshot::getQuoteTime).last("limit " + safeDays))
                .stream().sorted((a, b) -> a.getQuoteTime().compareTo(b.getQuoteTime())).toList();
    }

    public StockFundFlowSnapshot latest(Long userId, Long watchId) {
        requireWatch(userId, watchId);
        return snapshots.selectOne(new LambdaQueryWrapper<StockFundFlowSnapshot>()
                .eq(StockFundFlowSnapshot::getUserId, userId).eq(StockFundFlowSnapshot::getWatchItemId, watchId)
                .orderByDesc(StockFundFlowSnapshot::getQuoteTime).last("limit 1"));
    }

    public StockFundFlowStatusResponse status(Long userId) {
        List<StockWatchItem> items = enabledCnWatches(userId);
        List<Long> ids = items.stream().map(StockWatchItem::getId).toList();
        List<StockFundFlowSnapshot> latest = latestSnapshots(userId, ids);
        List<StockFundFlowCollection> recent = ids.isEmpty() ? List.of() : collections.selectList(
                new LambdaQueryWrapper<StockFundFlowCollection>().eq(StockFundFlowCollection::getUserId, userId)
                        .in(StockFundFlowCollection::getWatchItemId, ids)
                        .orderByDesc(StockFundFlowCollection::getCollectedAt).last("limit 50"));
        Map<Long, String> names = items.stream().collect(Collectors.toMap(StockWatchItem::getId, StockWatchItem::getStockName));
        LocalDateTime quoteTime = latest.stream().map(StockFundFlowSnapshot::getQuoteTime).max(LocalDateTime::compareTo).orElse(null);
        LocalDateTime refreshTime = recent.stream().map(StockFundFlowCollection::getCollectedAt).max(LocalDateTime::compareTo).orElse(null);
        List<StockFundFlowStatusResponse.Failure> failures = recent.stream().filter(row -> !Boolean.TRUE.equals(row.getSuccess()))
                .limit(5).map(row -> new StockFundFlowStatusResponse.Failure(row.getWatchItemId(),
                        names.getOrDefault(row.getWatchItemId(), "#" + row.getWatchItemId()), row.getErrorMessage(), row.getCollectedAt())).toList();
        return new StockFundFlowStatusResponse(items.size(), latest.size(), items.size() - latest.size(),
                rate(latest.size(), items.size()), quoteTime, refreshTime,
                (int) recent.stream().filter(row -> Boolean.TRUE.equals(row.getSuccess())).count(),
                (int) recent.stream().filter(row -> !Boolean.TRUE.equals(row.getSuccess())).count(), failures);
    }

    private StockFundFlowRefreshResponse refreshItems(List<StockWatchItem> items) {
        LocalDateTime now = LocalDateTime.now();
        List<StockFundFlowRefreshResponse.Item> output = new ArrayList<>();
        for (StockWatchItem item : items) {
            try {
                List<StockFundFlowPoint> points = provider.fetchDaily(item, HISTORY_LIMIT);
                points.forEach(point -> upsert(item, point, now));
                saveCollection(item, true, points.size(), null, now);
                output.add(new StockFundFlowRefreshResponse.Item(item.getId(), item.getStockCode(), item.getStockName(),
                        true, points.size(), "刷新成功"));
            } catch (Exception exception) {
                saveCollection(item, false, 0, exception.getMessage(), now);
                output.add(new StockFundFlowRefreshResponse.Item(item.getId(), item.getStockCode(), item.getStockName(),
                        false, 0, exception.getMessage()));
            }
        }
        int success = (int) output.stream().filter(StockFundFlowRefreshResponse.Item::success).count();
        return new StockFundFlowRefreshResponse(output.size(), success, output.size() - success, now, output);
    }

    private void upsert(StockWatchItem item, StockFundFlowPoint point, LocalDateTime collectedAt) {
        StockFundFlowSnapshot snapshot = snapshots.selectOne(new LambdaQueryWrapper<StockFundFlowSnapshot>()
                .eq(StockFundFlowSnapshot::getUserId, item.getUserId()).eq(StockFundFlowSnapshot::getWatchItemId, item.getId())
                .eq(StockFundFlowSnapshot::getProvider, provider.name()).eq(StockFundFlowSnapshot::getPeriodType, PERIOD_DAILY)
                .eq(StockFundFlowSnapshot::getQuoteTime, point.quoteTime()));
        boolean create = snapshot == null;
        if (create) snapshot = new StockFundFlowSnapshot();
        snapshot.setUserId(item.getUserId()); snapshot.setWatchItemId(item.getId()); snapshot.setStockCode(item.getStockCode()); snapshot.setMarket(item.getMarket());
        snapshot.setMainNetInflow(point.mainNetInflow()); snapshot.setMainNetRatio(point.mainNetRatio()); snapshot.setSuperLargeNetInflow(point.superLargeNetInflow()); snapshot.setSuperLargeNetRatio(point.superLargeNetRatio());
        snapshot.setLargeNetInflow(point.largeNetInflow()); snapshot.setLargeNetRatio(point.largeNetRatio()); snapshot.setMediumNetInflow(point.mediumNetInflow()); snapshot.setMediumNetRatio(point.mediumNetRatio());
        snapshot.setSmallNetInflow(point.smallNetInflow()); snapshot.setSmallNetRatio(point.smallNetRatio()); snapshot.setLatestPrice(point.latestPrice()); snapshot.setChangePercent(point.changePercent()); snapshot.setTurnoverAmount(point.turnoverAmount());
        snapshot.setProvider(provider.name()); snapshot.setPeriodType(PERIOD_DAILY); snapshot.setQuoteTime(point.quoteTime()); snapshot.setCollectedAt(collectedAt);
        if (create) snapshots.insert(snapshot); else snapshots.updateById(snapshot);
    }

    private void saveCollection(StockWatchItem item, boolean success, int count, String error, LocalDateTime time) {
        StockFundFlowCollection row = new StockFundFlowCollection(); row.setUserId(item.getUserId()); row.setWatchItemId(item.getId());
        row.setSuccess(success); row.setSnapshotCount(count); row.setProvider(provider.name()); row.setErrorMessage(error); row.setCollectedAt(time); collections.insert(row);
    }

    private List<StockWatchItem> enabledCnWatches(Long userId) {
        return watches.selectList(new LambdaQueryWrapper<StockWatchItem>().eq(StockWatchItem::getUserId, userId)
                .eq(StockWatchItem::getEnabled, true).eq(StockWatchItem::getMarket, "CN").orderByAsc(StockWatchItem::getStockCode));
    }

    private List<StockFundFlowSnapshot> latestSnapshots(Long userId, List<Long> watchIds) {
        if (watchIds.isEmpty()) return List.of();
        List<StockFundFlowSnapshot> rows = snapshots.selectList(new LambdaQueryWrapper<StockFundFlowSnapshot>()
                .eq(StockFundFlowSnapshot::getUserId, userId).in(StockFundFlowSnapshot::getWatchItemId, watchIds)
                .eq(StockFundFlowSnapshot::getPeriodType, PERIOD_DAILY).orderByDesc(StockFundFlowSnapshot::getQuoteTime));
        Map<Long, StockFundFlowSnapshot> latest = new LinkedHashMap<>();
        rows.forEach(row -> latest.putIfAbsent(row.getWatchItemId(), row));
        return new ArrayList<>(latest.values());
    }

    private StockWatchItem requireWatch(Long userId, Long watchId) {
        StockWatchItem item = watches.selectById(watchId);
        if (item == null || !userId.equals(item.getUserId())) throw new BusinessException(ErrorCode.NOT_FOUND, "股票关注项不存在");
        return item;
    }

    private BigDecimal rate(int part, int total) { return total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(part * 100L).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP); }
    private boolean positive(BigDecimal value) { return value != null && value.signum() > 0; }
    private boolean negative(BigDecimal value) { return value != null && value.signum() < 0; }
    private BigDecimal nullSafe(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
