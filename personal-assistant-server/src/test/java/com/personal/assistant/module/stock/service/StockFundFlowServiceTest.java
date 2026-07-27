package com.personal.assistant.module.stock.service;

import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.module.stock.entity.StockFundFlowCollection;
import com.personal.assistant.module.stock.entity.StockFundFlowSnapshot;
import com.personal.assistant.module.stock.entity.StockWatchItem;
import com.personal.assistant.module.stock.mapper.StockFundFlowCollectionMapper;
import com.personal.assistant.module.stock.mapper.StockFundFlowSnapshotMapper;
import com.personal.assistant.module.stock.mapper.StockWatchItemMapper;
import com.personal.assistant.module.stock.provider.StockFundFlowPoint;
import com.personal.assistant.module.stock.provider.StockFundFlowProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockFundFlowServiceTest {
    @Mock StockWatchItemMapper watches;
    @Mock StockFundFlowSnapshotMapper snapshots;
    @Mock StockFundFlowCollectionMapper collections;
    @Mock StockFundFlowProvider provider;
    StockFundFlowService service;

    @BeforeEach void setUp() { service = new StockFundFlowService(watches, snapshots, collections, provider); }

    @Test
    void refreshStoresProviderPointsAndCollectionResult() {
        StockWatchItem item = watch(1L, 7L, "600000", "浦发银行");
        when(watches.selectList(any())).thenReturn(List.of(item));
        when(provider.name()).thenReturn("TEST");
        when(provider.fetchDaily(item, 20)).thenReturn(List.of(point(100), point(-20)));
        when(snapshots.selectOne(any())).thenReturn(null);

        var response = service.refresh(7L);

        assertEquals(1, response.success());
        verify(snapshots, org.mockito.Mockito.times(2)).insert(any(StockFundFlowSnapshot.class));
        ArgumentCaptor<StockFundFlowCollection> captor = ArgumentCaptor.forClass(StockFundFlowCollection.class);
        verify(collections).insert(captor.capture());
        assertEquals(2, captor.getValue().getSnapshotCount());
    }

    @Test
    void refreshUpdatesExistingSnapshotForSamePoint() {
        StockWatchItem item = watch(1L, 7L, "600000", "浦发银行");
        StockFundFlowSnapshot existing = new StockFundFlowSnapshot(); existing.setId(9L);
        when(watches.selectList(any())).thenReturn(List.of(item));
        when(provider.name()).thenReturn("TEST");
        when(provider.fetchDaily(item, 20)).thenReturn(List.of(point(100)));
        when(snapshots.selectOne(any())).thenReturn(existing);

        service.refresh(7L);

        verify(snapshots).updateById(existing);
    }

    @Test
    void overviewCalculatesCoverageAndRanking() {
        StockWatchItem first = watch(1L, 7L, "600000", "浦发银行");
        StockWatchItem second = watch(2L, 7L, "000001", "平安银行");
        StockFundFlowSnapshot flow = new StockFundFlowSnapshot();
        flow.setWatchItemId(1L); flow.setStockCode("600000"); flow.setMainNetInflow(BigDecimal.valueOf(500));
        flow.setQuoteTime(LocalDateTime.now());
        when(watches.selectList(any())).thenReturn(List.of(first, second));
        when(snapshots.selectList(any())).thenReturn(List.of(flow));
        when(provider.name()).thenReturn("TEST");

        var overview = service.overview(7L);

        assertEquals(2, overview.watchCount());
        assertEquals(1, overview.coveredCount());
        assertEquals(0, BigDecimal.valueOf(50).compareTo(overview.coverageRate()));
        assertEquals("浦发银行", overview.ranking().get(0).stockName());
    }

    @Test
    void trendRejectsAnotherUsersWatch() {
        when(watches.selectById(1L)).thenReturn(watch(1L, 8L, "600000", "浦发银行"));
        assertThrows(BusinessException.class, () -> service.trend(7L, 1L, 20));
    }

    private StockWatchItem watch(Long id, Long userId, String code, String name) {
        StockWatchItem item = new StockWatchItem(); item.setId(id); item.setUserId(userId); item.setStockCode(code);
        item.setStockName(name); item.setMarket("CN"); item.setEnabled(true); return item;
    }

    private StockFundFlowPoint point(long mainFlow) {
        return new StockFundFlowPoint(BigDecimal.valueOf(mainFlow), BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.TEN, BigDecimal.ONE, null, LocalDateTime.now().withNano(0));
    }
}
