package com.personal.assistant.module.stock.service;

import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.module.reminder.service.SecretCryptoService;
import com.personal.assistant.module.stock.dto.StockApiConfigRequest;
import com.personal.assistant.module.stock.dto.StockMarketMapResponse;
import com.personal.assistant.module.stock.dto.StockQuoteStatusResponse;
import com.personal.assistant.module.stock.dto.StockWatchRequest;
import com.personal.assistant.module.stock.entity.StockApiConfig;
import com.personal.assistant.module.stock.entity.StockCollectionResult;
import com.personal.assistant.module.stock.entity.StockWatchItem;
import com.personal.assistant.module.stock.mapper.StockApiConfigMapper;
import com.personal.assistant.module.stock.mapper.StockCollectionResultMapper;
import com.personal.assistant.module.stock.mapper.StockWatchItemMapper;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {
    @Mock
    StockWatchItemMapper watches;
    @Mock
    StockApiConfigMapper configs;
    @Mock
    StockCollectionResultMapper results;
    @Mock
    SecretCryptoService crypto;

    StockService service;

    @BeforeEach
    void setUp() {
        service = new StockService(watches, configs, results, crypto);
    }

    @Test
    void createWatchNormalizesCodeAndOwner() {
        service.saveWatch(7L, null, new StockWatchRequest(" aapl ", "Apple", "US", "科技",
                BigDecimal.valueOf(198.2), BigDecimal.valueOf(1.25), BigDecimal.valueOf(3000), null,
                "科技", "长期", null, true));

        ArgumentCaptor<StockWatchItem> captor = ArgumentCaptor.forClass(StockWatchItem.class);
        verify(watches).insert(captor.capture());
        assertEquals("AAPL", captor.getValue().getStockCode());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals("科技", captor.getValue().getIndustry());
        assertEquals(0, BigDecimal.valueOf(1.25).compareTo(captor.getValue().getChangePercent()));
    }

    @Test
    void rejectUnsupportedMarket() {
        assertThrows(BusinessException.class, () -> service.saveWatch(7L, null,
                new StockWatchRequest("AAPL", "Apple", "EU", null, null, null, null, null,
                        null, null, null, true)));
    }

    @Test
    void apiKeyIsEncrypted() {
        when(crypto.encrypt("secret")).thenReturn("encrypted");
        service.saveConfig(7L, null, new StockApiConfigRequest("接口", "行情", "https://example.com/{code}",
                "BEARER", "secret", 60, true));

        ArgumentCaptor<StockApiConfig> captor = ArgumentCaptor.forClass(StockApiConfig.class);
        verify(configs).insert(captor.capture());
        assertEquals("encrypted", captor.getValue().getApiKeyEncrypted());
    }

    @Test
    void schedulerRefreshWritesFailureForUnsupportedMarket() {
        StockWatchItem watch = new StockWatchItem();
        watch.setId(3L);
        watch.setUserId(7L);
        watch.setStockCode("AAPL");
        watch.setStockName("Apple");
        watch.setMarket("US");
        watch.setEnabled(true);
        when(watches.selectList(any())).thenReturn(List.of(watch));

        assertEquals(1, service.collectEnabled());
        ArgumentCaptor<StockCollectionResult> captor = ArgumentCaptor.forClass(StockCollectionResult.class);
        verify(results).insert(captor.capture());
        assertFalse(captor.getValue().getSuccess());
        assertTrue(captor.getValue().getErrorMessage().contains("仅支持 A 股"));
    }

    @Test
    void marketMapGroupsByIndustryAndCalculatesStats() {
        StockWatchItem up = watch(1L, "600000", "浦发银行", "银行", 1.2, 5000);
        StockWatchItem down = watch(2L, "688981", "中芯国际", "半导体", -2.5, 8000);
        StockWatchItem flat = watch(3L, "000001", "平安银行", null, 0, null);
        when(watches.selectList(any())).thenReturn(List.of(up, down, flat));

        StockMarketMapResponse response = service.marketMap(7L, "CN", true);

        assertEquals("CN", response.market());
        assertEquals(3, response.stats().total());
        assertEquals(1, response.stats().up());
        assertEquals(1, response.stats().down());
        assertEquals(1, response.stats().flat());
        assertEquals(3, response.industries().size());
        assertTrue(response.industries().stream().anyMatch(i -> "未分组".equals(i.industry())));
        assertEquals(BigDecimal.valueOf(100), response.industries().stream()
                .filter(i -> "未分组".equals(i.industry()))
                .findFirst().orElseThrow().children().get(0).weight());
    }

    @Test
    void quoteStatusSummarizesLocalQuoteAndRecentFailures() {
        StockWatchItem quoted = watch(1L, "600000", "浦发银行", "银行", 1.2, 5000);
        quoted.setLatestPrice(BigDecimal.valueOf(10.2));
        quoted.setQuoteTime(LocalDateTime.now().minusMinutes(5));
        StockWatchItem missing = watch(2L, "688981", "中芯国际", "半导体", -2.5, 8000);
        StockCollectionResult failure = new StockCollectionResult();
        failure.setWatchItemId(2L);
        failure.setSuccess(false);
        failure.setErrorMessage("网络超时");
        failure.setCollectedAt(LocalDateTime.now());
        when(watches.selectList(any())).thenReturn(List.of(quoted, missing));
        when(results.selectList(any())).thenReturn(List.of(failure));

        StockQuoteStatusResponse status = service.quoteStatus(7L, "CN", true);

        assertEquals(2, status.watchCount());
        assertEquals(1, status.quotedCount());
        assertEquals(1, status.missingQuoteCount());
        assertEquals(0, status.recentSuccess());
        assertEquals(1, status.recentFailed());
        assertEquals("中芯国际", status.recentFailures().get(0).stockName());
    }

    private StockWatchItem watch(Long id, String code, String name, String industry, double change, Integer marketValue) {
        StockWatchItem item = new StockWatchItem();
        item.setId(id);
        item.setUserId(7L);
        item.setStockCode(code);
        item.setStockName(name);
        item.setMarket("CN");
        item.setIndustry(industry);
        item.setChangePercent(BigDecimal.valueOf(change));
        item.setMarketValue(marketValue == null ? null : BigDecimal.valueOf(marketValue));
        item.setEnabled(true);
        return item;
    }
}