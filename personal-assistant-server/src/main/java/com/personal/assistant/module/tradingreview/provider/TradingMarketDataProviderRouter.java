package com.personal.assistant.module.tradingreview.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personal.assistant.module.tradingreview.dto.MarketSnapshot;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@Primary
public class TradingMarketDataProviderRouter implements TradingMarketDataProvider {
    private final IfindTradingMarketDataProvider ifind;
    private final EastMoneyDetailedMarketDataProvider eastMoney;
    private final ObjectMapper objectMapper;

    public TradingMarketDataProviderRouter(IfindTradingMarketDataProvider ifind,
                                           EastMoneyDetailedMarketDataProvider eastMoney,
                                           ObjectMapper objectMapper) {
        this.ifind = ifind;
        this.eastMoney = eastMoney;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() { return "ROUTER"; }

    @Override
    public MarketSnapshot fetch(LocalDate tradeDate) {
        if (ifind.available()) {
            try {
                return markQuality(ifind.fetch(tradeDate), "IFIND", false, null);
            } catch (RuntimeException exception) {
                MarketSnapshot fallback = eastMoney.fetch(tradeDate);
                return markQuality(fallback, "EASTMONEY", true, rootMessage(exception));
            }
        }
        return markQuality(eastMoney.fetch(tradeDate), "EASTMONEY", false, "IFIND_DISABLED");
    }

    private MarketSnapshot markQuality(MarketSnapshot snapshot, String actualSource, boolean degraded, String reason) {
        String raw = enrichRaw(snapshot.rawMetrics(), actualSource, degraded, reason);
        String source = degraded ? actualSource + "(iFinD降级)" : actualSource;
        return new MarketSnapshot(snapshot.shanghaiChange(), snapshot.shenzhenChange(), snapshot.chinextChange(),
                snapshot.risingCount(), snapshot.fallingCount(), snapshot.flatCount(), snapshot.limitUpCount(),
                snapshot.limitDownCount(), snapshot.brokenBoardCount(), snapshot.brokenBoardRate(), snapshot.maxStreak(),
                snapshot.turnoverAmount(), snapshot.turnoverChange(), snapshot.industrySectors(), snapshot.conceptSectors(),
                source, snapshot.quoteTime(), raw);
    }

    private String enrichRaw(String rawMetrics, String actualSource, boolean degraded, String reason) {
        try {
            ObjectNode root = rawMetrics == null || rawMetrics.isBlank()
                    ? objectMapper.createObjectNode() : (ObjectNode) objectMapper.readTree(rawMetrics);
            ObjectNode quality = root.withObject("/dataQuality");
            quality.put("preferredSource", "IFIND");
            quality.put("actualSource", actualSource);
            quality.put("degraded", degraded);
            if (reason != null) quality.put("degradeReason", reason);
            return root.toString();
        } catch (Exception ignored) {
            return rawMetrics;
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}
