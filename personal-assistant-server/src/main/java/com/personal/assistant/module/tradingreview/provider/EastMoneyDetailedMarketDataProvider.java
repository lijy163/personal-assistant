package com.personal.assistant.module.tradingreview.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.personal.assistant.module.tradingreview.dto.MarketSnapshot;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

@Primary
@Component
public class EastMoneyDetailedMarketDataProvider implements TradingMarketDataProvider {
    private static final Logger log = LoggerFactory.getLogger(EastMoneyDetailedMarketDataProvider.class);
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final String BASE = "https://push2delay.eastmoney.com";
    private static final String INDEX_URL = BASE + "/api/qt/ulist.np/get?fltt=2&secids=1.000001,0.399001,0.399006&fields=f3,f6,f104,f105,f106";
    private static final String SECTOR_URL = BASE + "/api/qt/clist/get?pn=1&pz=500&po=%d&np=1&fltt=2&invt=2&fid=%s&fs=m:90+t:2&fields=f12,f14,f3,f6,f104,f105,f106,f128,f136";
    private static final Set<String> PRIMARY_INDUSTRIES = Set.of("农林牧渔","基础化工","钢铁","有色金属","电子","汽车","家用电器","食品饮料","纺织服饰","轻工制造","医药生物","公用事业","交通运输","房地产","商贸零售","社会服务","综合","建筑材料","建筑装饰","电力设备","国防军工","计算机","传媒","通信","银行","非银金融","美容护理","煤炭","石油石化","环保","机械设备");
    private static final Set<String> SECONDARY_INDUSTRIES = Set.of("\u534a\u5bfc\u4f53","\u5143\u4ef6","\u5149\u5b66\u5149\u7535\u5b50","\u5176\u4ed6\u7535\u5b50","\u4e58\u7528\u8f66","\u5546\u7528\u8f66","\u6c7d\u8f66\u96f6\u90e8\u4ef6","\u6c7d\u8f66\u670d\u52a1","\u7535\u6c60","\u7535\u673a","\u767d\u8272\u5bb6\u7535","\u9ed1\u8272\u5bb6\u7535","\u5c0f\u5bb6\u7535","\u996e\u6599\u4e73\u54c1","\u98df\u54c1\u52a0\u5de5","\u5316\u5b66\u5236\u836f","\u4e2d\u836f","\u751f\u7269\u5236\u54c1","\u533b\u7597\u5668\u68b0","\u533b\u7597\u670d\u52a1","\u7535\u529b","\u71c3\u6c14","\u7269\u6d41","\u623f\u5730\u4ea7\u5f00\u53d1","\u8f6f\u4ef6\u5f00\u53d1","\u901a\u4fe1\u8bbe\u5907","\u901a\u4fe1\u670d\u52a1","\u8bc1\u5238","\u4fdd\u9669","\u7164\u70ad\u5f00\u91c7","\u6cb9\u6c14\u5f00\u91c7","\u4e13\u7528\u8bbe\u5907","\u901a\u7528\u8bbe\u5907","\u81ea\u52a8\u5316\u8bbe\u5907","\u5de5\u7a0b\u673a\u68b0");
    private static final String LIMIT_UP_URL = "https://push2ex.eastmoney.com/getTopicZTPool?ut=7eea3edcaed734bea9cbfc24409ed989&dpt=wz.ztzt&Pageindex=0&pagesize=500&sort=fbt:asc&date=%s";
    private static final String LIMIT_DOWN_URL = "https://push2ex.eastmoney.com/getTopicDTPool?ut=7eea3edcaed734bea9cbfc24409ed989&dpt=wz.ztzt&Pageindex=0&pagesize=500&sort=fbt:asc&date=%s";
    private final EastMoneyBoardMetricsDataProvider baseProvider;
    private final ObjectMapper objectMapper;
    private final RestClient client;

    public EastMoneyDetailedMarketDataProvider(EastMoneyBoardMetricsDataProvider baseProvider,
                                               ObjectMapper objectMapper) {
        this.baseProvider = baseProvider;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(8_000);
        requestFactory.setReadTimeout(15_000);
        this.client = RestClient.builder().requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 personal-assistant")
                .defaultHeader(HttpHeaders.CONNECTION, "close").build();
    }

    @Override
    public String name() { return baseProvider.name(); }

    @Override
    public MarketSnapshot fetch(LocalDate tradeDate) {
        MarketSnapshot base = baseProvider.fetch(tradeDate);
        ObjectNode raw = objectMapper.createObjectNode();
        raw.put("tradeDate", tradeDate.toString());
        ArrayNode warnings = raw.putArray("warnings");
        JsonNode indices = safely("指数明细", () -> get(INDEX_URL).path("data").path("diff"), warnings);
        int rising = validArray(indices) ? sum(indices, "f104") : value(base.risingCount());
        int falling = validArray(indices) ? sum(indices, "f105") : value(base.fallingCount());
        int flat = validArray(indices) ? sum(indices, "f106") : value(base.flatCount());
        BigDecimal turnover = validArray(indices) ? sumDecimal(indices, "f6") : base.turnoverAmount();
        raw.put("marketCount", rising + falling + flat);
        copyMarketMedian(base.rawMetrics(), raw);
        raw.put("breadthMethod", validArray(indices) ? "上证与深证指数行情涨跌家数汇总" : "基础行情降级数据");
        raw.set("indices", validArray(indices) ? indexMetrics(indices) : objectMapper.createArrayNode());
        ObjectNode meta = raw.putObject("sectorRankingMeta");
        meta.put("source", "东方财富申万行业").put("defaultLevel", 1).put("limitUpLevel", "细分行业");
        ObjectNode rankings = raw.putObject("sectorRankings");
        rankings.set("turnover", safelyArray("??????", () -> sectorRanking("f6", 1, 1), warnings));
        rankings.set("rising", safelyArray("?????", () -> sectorRanking("f3", 1, 1), warnings));
        rankings.set("falling", safelyArray("?????", () -> sectorRanking("f3", 0, 1), warnings));
        rankings.set("turnoverL2", safelyArray("????????", () -> sectorRanking("f6", 1, 2), warnings));
        rankings.set("risingL2", safelyArray("???????", () -> sectorRanking("f3", 1, 2), warnings));
        rankings.set("fallingL2", safelyArray("???????", () -> sectorRanking("f3", 0, 2), warnings));
        rankings.set("turnoverL3", safelyArray("????????", () -> sectorRanking("f6", 1, 3), warnings));
        rankings.set("risingL3", safelyArray("???????", () -> sectorRanking("f3", 1, 3), warnings));
        rankings.set("fallingL3", safelyArray("???????", () -> sectorRanking("f3", 0, 3), warnings));
        JsonNode limitUpPool = safely("涨停池明细", () -> limitUpPool(tradeDate), warnings);
        rankings.set("limitUp", validArray(limitUpPool) ? limitUpSectorRanking(limitUpPool) : objectMapper.createArrayNode());
        raw.set("streakLadder", validArray(limitUpPool) ? streakLadder(limitUpPool) : objectMapper.createArrayNode());
        raw.set("limitUpStocks", validArray(limitUpPool) ? limitUpStocks(limitUpPool) : objectMapper.createArrayNode());
        Integer limitDown = safely("跌停池", () -> limitDownCount(tradeDate), warnings);
        raw.put("degraded", !warnings.isEmpty());
        raw.putObject("dataQuality").put("source", name()).put("status", warnings.isEmpty() ? "COMPLETE" : "DEGRADED")
                .put("warningCount", warnings.size()).put("collectedAt", LocalDateTime.now(SHANGHAI).toString())
                .put("marketMedianAvailable", raw.path("marketMedian").hasNonNull("change"));
        return new MarketSnapshot(base.shanghaiChange(), base.shenzhenChange(), base.chinextChange(),
                rising, falling, flat, base.limitUpCount(), limitDown == null ? base.limitDownCount() : limitDown,
                base.brokenBoardCount(), base.brokenBoardRate(), base.maxStreak(), turnover,
                base.turnoverChange(), base.industrySectors(), base.conceptSectors(), name(),
                LocalDateTime.now(SHANGHAI), raw.toString());
    }

    private void copyMarketMedian(String sourceRaw, ObjectNode target) {
        if (sourceRaw == null || sourceRaw.isBlank()) return;
        try {
            JsonNode wrapped = objectMapper.readTree(sourceRaw).path("marketMetrics");
            JsonNode metrics = wrapped.isTextual() ? objectMapper.readTree(wrapped.asText()) : wrapped;
            JsonNode median = metrics.path("marketMedian");
            if (median.isObject()) target.set("marketMedian", median);
        } catch (Exception ignored) {}
    }

    private ArrayNode indexMetrics(JsonNode indices) {
        ArrayNode result = objectMapper.createArrayNode();
        String[] names = {"上证", "深证", "创业板"};
        for (int index = 0; index < indices.size(); index++) {
            JsonNode value = indices.get(index);
            result.addObject().put("name", names[index]).put("change", decimal(value, "f3"))
                    .put("turnover", decimal(value, "f6")).put("rising", value.path("f104").asInt())
                    .put("falling", value.path("f105").asInt()).put("flat", value.path("f106").asInt());
        }
        return result;
    }

    private ArrayNode sectorRanking(String field, int order, int level) {
        JsonNode rows = get(SECTOR_URL.formatted(order, field)).path("data").path("diff");
        List<JsonNode> matched = new ArrayList<>();
        for (JsonNode row : rows) if (industryLevel(row.path("f14").asText()) == level) matched.add(row);
        Comparator<JsonNode> comparator = Comparator.comparing(row -> decimal(row, field));
        matched.sort(order == 1 ? comparator.reversed() : comparator);
        ArrayNode result = objectMapper.createArrayNode();
        matched.stream().limit(10).forEach(row -> result.addObject()
                .put("code", row.path("f12").asText()).put("name", row.path("f14").asText()).put("level", level)
                .put("change", decimal(row, "f3")).put("turnover", decimal(row, "f6"))
                .put("rising", row.path("f104").asInt()).put("falling", row.path("f105").asInt())
                .put("flat", row.path("f106").asInt()).put("leader", row.path("f128").asText())
                .put("leaderChange", decimal(row, "f136")));
        return result;
    }

    private int industryLevel(String name) {
        if (PRIMARY_INDUSTRIES.contains(name)) return 1;
        return SECONDARY_INDUSTRIES.contains(name) ? 2 : 3;
    }

    private int limitDownCount(LocalDate tradeDate) {
        return get(LIMIT_DOWN_URL.formatted(tradeDate.format(DateTimeFormatter.BASIC_ISO_DATE)))
                .path("data").path("tc").asInt();
    }

    private JsonNode limitUpPool(LocalDate tradeDate) {
        return get(LIMIT_UP_URL.formatted(tradeDate.format(DateTimeFormatter.BASIC_ISO_DATE)))
                .path("data").path("pool");
    }

    private ArrayNode limitUpSectorRanking(JsonNode pool) {
        Map<String, Integer> counts = new HashMap<>();
        for (JsonNode stock : pool) counts.merge(stock.path("hybk").asText("其他"), 1, Integer::sum);
        ArrayNode result = objectMapper.createArrayNode();
        counts.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10).forEach(entry -> result.addObject().put("name", entry.getKey()).put("levelLabel", "细分行业").put("limitUpCount", entry.getValue()));
        return result;
    }

    private ArrayNode limitUpStocks(JsonNode pool) {
        ArrayNode result=objectMapper.createArrayNode();
        for(JsonNode stock:pool){int fallback=stock.path("zttj").path("ct").asInt(1);int streak=Math.max(1,stock.path("lbc").asInt(fallback));
            result.addObject().put("code",stock.path("c").asText()).put("name",stock.path("n").asText()).put("streak",streak);}
        return result;
    }

    private ArrayNode streakLadder(JsonNode pool) {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (JsonNode stock : pool) {
            int fallback = stock.path("zttj").path("ct").asInt(1);
            int streak = Math.max(1, stock.path("lbc").asInt(fallback));
            counts.merge(Math.min(streak, 4), 1, Integer::sum);
        }
        ArrayNode result = objectMapper.createArrayNode();
        for (int streak = 1; streak <= 4; streak++)
            result.addObject().put("level", streak).put("label", streak == 4 ? "4板及以上" : streak + "板")
                    .put("count", counts.getOrDefault(streak, 0));
        return result;
    }

    private ArrayNode safelyArray(String label, Supplier<ArrayNode> supplier, ArrayNode warnings) {
        ArrayNode result = safely(label, supplier, warnings);
        return result == null ? objectMapper.createArrayNode() : result;
    }

    private <T> T safely(String label, Supplier<T> supplier, ArrayNode warnings) {
        try { return supplier.get(); }
        catch (RuntimeException exception) {
            warnings.add(label + "获取失败：" + rootMessage(exception));
            log.warn("交易复盘{}获取失败，已降级保留其他数据", label, exception);
            return null;
        }
    }

    private boolean validArray(JsonNode value) { return value != null && value.isArray() && !value.isEmpty(); }
    private int value(Integer number) { return number == null ? 0 : number; }
    private String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    private JsonNode get(String url) {
        String body = client.get().uri(url + (url.contains("?") ? "&" : "?") + "_=" + System.currentTimeMillis())
                .retrieve().body(String.class);
        try { return objectMapper.readTree(body); }
        catch (Exception exception) { throw new IllegalStateException("市场明细响应解析失败", exception); }
    }

    private int sum(JsonNode values, String field) {
        int result = 0;
        for (int index = 0; index < Math.min(2, values.size()); index++) result += values.get(index).path(field).asInt();
        return result;
    }

    private BigDecimal sumDecimal(JsonNode values, String field) {
        BigDecimal result = BigDecimal.ZERO;
        for (int index = 0; index < Math.min(2, values.size()); index++) result = result.add(decimal(values.get(index), field));
        return result;
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.decimalValue() : BigDecimal.ZERO;
    }
}
