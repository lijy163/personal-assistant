package com.personal.assistant.module.tradingreview.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.assistant.module.tradingreview.dto.MarketSnapshot;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class EastMoneyBoardMetricsDataProvider implements TradingMarketDataProvider {
    private static final String LIMIT_UP_POOL_URL = "https://push2ex.eastmoney.com/getTopicZTPool?ut=7eea3edcaed734bea9cbfc24409ed989&dpt=wz.ztzt&Pageindex=0&pagesize=500&sort=fbt:asc&date=%s";
    private static final String BROKEN_BOARD_POOL_URL = "https://push2ex.eastmoney.com/getTopicZBPool?ut=7eea3edcaed734bea9cbfc24409ed989&dpt=wz.ztzt&Pageindex=0&pagesize=500&sort=fbt:asc&date=%s";
    private final EastMoneyTradingMarketDataProvider marketDataProvider;
    private final ObjectMapper objectMapper;
    private final RestClient client;

    public EastMoneyBoardMetricsDataProvider(EastMoneyTradingMarketDataProvider marketDataProvider,
                                             ObjectMapper objectMapper) {
        this.marketDataProvider = marketDataProvider;
        this.objectMapper = objectMapper;
        this.client = RestClient.builder().defaultHeader("User-Agent", "Mozilla/5.0 personal-assistant").build();
    }

    @Override public String name() { return marketDataProvider.name(); }

    @Override
    public MarketSnapshot fetch(LocalDate tradeDate) {
        MarketSnapshot market = marketDataProvider.fetch(tradeDate);
        try {
            String date = tradeDate.format(DateTimeFormatter.BASIC_ISO_DATE);
            BoardMetrics board = parseBoardMetrics(get(LIMIT_UP_POOL_URL.formatted(date)), get(BROKEN_BOARD_POOL_URL.formatted(date)));
            String rawMetrics = objectMapper.createObjectNode().put("marketMetrics", market.rawMetrics())
                    .put("limitMethod", "东方财富涨停池").put("brokenBoardMethod", "东方财富炸板池")
                    .put("brokenBoardRateMethod", "炸板数/(封板数+炸板数)").put("streakMethod", "涨停池最高连板数").toString();
            return new MarketSnapshot(market.shanghaiChange(), market.shenzhenChange(), market.chinextChange(),
                    market.risingCount(), market.fallingCount(), market.flatCount(), board.limitUpCount(),
                    market.limitDownCount(), board.brokenBoardCount(), board.brokenBoardRate(), board.maxStreak(),
                    market.turnoverAmount(), market.turnoverChange(), market.industrySectors(),
                    market.conceptSectors(), name(), market.quoteTime(), rawMetrics);
        } catch (Exception exception) {
            String rawMetrics = objectMapper.createObjectNode().put("marketMetrics", market.rawMetrics())
                    .put("boardMetricsDegraded", true)
                    .put("boardMetricsWarning", "涨停炸板数据获取失败：" + rootMessage(exception)).toString();
            return new MarketSnapshot(market.shanghaiChange(), market.shenzhenChange(), market.chinextChange(),
                    market.risingCount(), market.fallingCount(), market.flatCount(), market.limitUpCount(),
                    market.limitDownCount(), null, null, null, market.turnoverAmount(), market.turnoverChange(),
                    market.industrySectors(), market.conceptSectors(), name(), market.quoteTime(), rawMetrics);
        }
    }

    BoardMetrics parseBoardMetrics(JsonNode limitUpResponse, JsonNode brokenBoardResponse) {
        JsonNode limitUpData = requirePoolData(limitUpResponse, "涨停池");
        JsonNode brokenBoardData = requirePoolData(brokenBoardResponse, "炸板池");
        int limitUpCount = limitUpData.path("tc").asInt();
        int brokenBoardCount = brokenBoardData.path("tc").asInt();
        int maxStreak = 0;
        for (JsonNode quote : limitUpData.path("pool")) {
            int fallbackStreak = quote.path("zttj").path("ct").asInt();
            maxStreak = Math.max(maxStreak, quote.path("lbc").asInt(fallbackStreak));
        }
        int touchedLimitCount = limitUpCount + brokenBoardCount;
        BigDecimal brokenBoardRate = touchedLimitCount == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(brokenBoardCount)
                .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(touchedLimitCount), 2, RoundingMode.HALF_UP);
        return new BoardMetrics(limitUpCount, brokenBoardCount, brokenBoardRate, maxStreak);
    }

    private JsonNode requirePoolData(JsonNode response, String poolName) {
        JsonNode data = response.path("data");
        if (!data.isObject() || !data.has("tc") || !data.path("pool").isArray())
            throw new IllegalStateException("东方财富未返回" + poolName + "数据");
        return data;
    }

    private JsonNode get(String url) {
        String body = client.get().uri(url).retrieve().body(String.class);
        try { return objectMapper.readTree(body); }
        catch (Exception exception) { throw new IllegalStateException("涨停炸板响应解析失败", exception); }
    }

    private String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    record BoardMetrics(Integer limitUpCount, Integer brokenBoardCount, BigDecimal brokenBoardRate, Integer maxStreak) {}
}
