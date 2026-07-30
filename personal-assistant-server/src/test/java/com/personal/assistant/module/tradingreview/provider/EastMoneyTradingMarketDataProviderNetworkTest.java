package com.personal.assistant.module.tradingreview.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EastMoneyTradingMarketDataProviderNetworkTest {
    @Test
    void retriesTransientEmptyHeaderFailureAndUsesFallbackHost() {
        AtomicInteger attempts = new AtomicInteger();
        var provider = new EastMoneyTradingMarketDataProvider(new ObjectMapper(), url -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) throw new IllegalStateException("HTTP/1.1 header parser received no bytes");
            if (url.contains("clist/get")) return "{\"rc\":0,\"data\":{\"total\":1,\"diff\":[{\"f3\":1.2,\"f6\":100}]}}";
            if (url.contains("ulist.np/get")) return "{\"rc\":0,\"data\":{\"diff\":[{\"f3\":1.0},{\"f3\":1.1},{\"f3\":1.2}]}}";
            throw new IllegalStateException("unexpected url");
        });

        var result = provider.fetch(LocalDate.of(2026, 7, 30));

        assertEquals(1, result.risingCount());
        assertTrue(attempts.get() >= 5);
    }

    @Test
    void rotatesHostsAndAddsCacheBuster() {
        String url = "https://push2.eastmoney.com/api/qt/clist/get?pn=1";

        assertEquals("https://push2delay.eastmoney.com/api/qt/clist/get?pn=1&_=1000",
                EastMoneyTradingMarketDataProvider.alternateUrl(url, 0, 100));
        assertEquals("https://push2.eastmoney.com/api/qt/clist/get?pn=1&_=1001",
                EastMoneyTradingMarketDataProvider.alternateUrl(url, 1, 100));
        assertEquals("https://82.push2.eastmoney.com/api/qt/clist/get?pn=1&_=1002",
                EastMoneyTradingMarketDataProvider.alternateUrl(url, 2, 100));
        assertEquals("https://48.push2.eastmoney.com/api/qt/clist/get?pn=1&_=1003",
                EastMoneyTradingMarketDataProvider.alternateUrl(url, 3, 100));
    }
}
