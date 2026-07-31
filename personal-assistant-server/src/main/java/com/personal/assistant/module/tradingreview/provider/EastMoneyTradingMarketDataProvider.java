package com.personal.assistant.module.tradingreview.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.tradingreview.dto.MarketSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class EastMoneyTradingMarketDataProvider implements TradingMarketDataProvider {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final int MARKET_PAGE_SIZE = 500;
    private static final int MAX_MARKET_PAGES = 1;
    private static final int MAX_ATTEMPTS = 4;
    private static final long[] RETRY_DELAYS_MILLIS = {0, 400, 1_000, 2_000};
    private static final String[] EASTMONEY_HOSTS = {
            "push2delay.eastmoney.com", "push2.eastmoney.com",
            "82.push2.eastmoney.com", "48.push2.eastmoney.com"
    };
    private static final String MARKET_URL = "https://push2.eastmoney.com/api/qt/clist/get?pn=%d&pz=" + MARKET_PAGE_SIZE + "&po=1&np=1&fltt=2&invt=2&fid=f3&fs=m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23&fields=f3,f6";
    private static final String INDEX_URL = "https://push2.eastmoney.com/api/qt/ulist.np/get?fltt=2&secids=1.000001,0.399001,0.399006&fields=f3,f6";
    private static final String SECTOR_URL = "https://push2.eastmoney.com/api/qt/clist/get?pn=1&pz=8&po=1&np=1&fltt=2&invt=2&fid=f3&fs=%s&fields=f12,f14,f3";
    private final ObjectMapper objectMapper;
    private final HttpFetcher httpFetcher;
    private final Sleeper sleeper;

    @Autowired
    public EastMoneyTradingMarketDataProvider(ObjectMapper objectMapper) {
        this(objectMapper, createHttpFetcher(), Thread::sleep);
    }

    EastMoneyTradingMarketDataProvider(ObjectMapper objectMapper, HttpFetcher httpFetcher) {
        this(objectMapper, httpFetcher, ignored -> {});
    }

    EastMoneyTradingMarketDataProvider(ObjectMapper objectMapper, HttpFetcher httpFetcher, Sleeper sleeper) {
        this.objectMapper = objectMapper;
        this.httpFetcher = httpFetcher;
        this.sleeper = sleeper;
    }

    @Override
    public String name() { return "EASTMONEY"; }

    @Override
    public MarketSnapshot fetch(LocalDate tradeDate) {
        try {
            List<JsonNode> market = fetchMarketPages();
            if (market.isEmpty()) throw new IllegalStateException("东方财富未返回全市场行情");
            int rising = 0, falling = 0, flat = 0, limitUp = 0, limitDown = 0;
            BigDecimal turnover = BigDecimal.ZERO;
            for (JsonNode quote : market) {
                BigDecimal change = decimal(quote, "f3");
                BigDecimal amount = decimal(quote, "f6");
                if (amount != null) turnover = turnover.add(amount);
                if (change == null || change.signum() == 0) flat++;
                else if (change.signum() > 0) rising++;
                else falling++;
                if (change != null && change.compareTo(new BigDecimal("9.5")) >= 0) limitUp++;
                if (change != null && change.compareTo(new BigDecimal("-9.5")) <= 0) limitDown++;
            }
            JsonNode indices = get(INDEX_URL).path("data").path("diff");
            BigDecimal shanghai = index(indices, 0, "f3");
            BigDecimal shenzhen = index(indices, 1, "f3");
            BigDecimal chinext = index(indices, 2, "f3");
            String raw = objectMapper.createObjectNode()
                    .put("tradeDate", tradeDate.toString()).put("marketCount", market.size())
                    .put("marketPageSize", MARKET_PAGE_SIZE)
                    .put("limitMethod", "涨跌幅阈值近似，涨停池数据将覆盖该值").toString();
            return new MarketSnapshot(shanghai, shenzhen, chinext, rising, falling, flat, limitUp, limitDown,
                    null, null, null, turnover, null, sectors("m:90+t:2"), sectors("m:90+t:3"),
                    name(), LocalDateTime.now(SHANGHAI), raw);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "东方财富行情采集失败：" + rootMessage(exception));
        }
    }

    private List<JsonNode> fetchMarketPages() {
        List<JsonNode> quotes = new ArrayList<>();
        for (int page = 1; page <= MAX_MARKET_PAGES; page++) {
            JsonNode data = get(MARKET_URL.formatted(page)).path("data");
            JsonNode rows = data.path("diff");
            if (!rows.isArray() || rows.isEmpty()) break;
            rows.forEach(quotes::add);
            int total = data.path("total").asInt();
            if (total > 0 && quotes.size() >= total) break;
        }
        return quotes;
    }

    private JsonNode get(String url) {
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String requestUrl = alternateUrl(url, attempt, System.currentTimeMillis());
            try {
                sleeper.sleep(RETRY_DELAYS_MILLIS[attempt]);
                String body = httpFetcher.get(requestUrl);
                if (body == null || body.isBlank()) throw new IllegalStateException("行情接口返回空响应");
                JsonNode response = objectMapper.readTree(body);
                if (response.has("rc") && response.path("rc").asInt() != 0)
                    throw new IllegalStateException("行情接口返回错误码 " + response.path("rc").asInt());
                return response;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("行情请求重试被中断", exception);
            } catch (Exception exception) {
                lastFailure = new IllegalStateException("请求 " + requestUrl + " 失败：" + rootMessage(exception), exception);
            }
        }
        throw lastFailure == null ? new IllegalStateException("行情请求失败") : lastFailure;
    }

    static String alternateUrl(String url, int attempt, long cacheBuster) {
        String switched = url.replace("push2.eastmoney.com", EASTMONEY_HOSTS[attempt % EASTMONEY_HOSTS.length]);
        return switched + (switched.contains("?") ? "&" : "?") + "_=" + cacheBuster + attempt;
    }

    private String sectors(String filter) {
        JsonNode rows = get(SECTOR_URL.formatted(filter)).path("data").path("diff");
        List<JsonNode> values = new ArrayList<>();
        rows.forEach(values::add);
        values.sort(Comparator.comparing(node -> decimal(node, "f3"), Comparator.nullsLast(Comparator.reverseOrder())));
        return values.stream().map(node -> "%s(%s%%)".formatted(node.path("f14").asText(), node.path("f3").asText("-"))).toList().toString();
    }

    private BigDecimal index(JsonNode rows, int position, String field) {
        return rows.isArray() && rows.size() > position ? decimal(rows.get(position), field) : null;
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || "-".equals(value.asText())) return null;
        try { return value.decimalValue(); } catch (Exception ignored) { return null; }
    }

    private static HttpFetcher createHttpFetcher() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(8_000);
        requestFactory.setReadTimeout(15_000);
        RestClient client = RestClient.builder().requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 personal-assistant")
                .defaultHeader(HttpHeaders.ACCEPT, "application/json,text/plain,*/*")
                .defaultHeader(HttpHeaders.REFERER, "https://quote.eastmoney.com/")
                .defaultHeader(HttpHeaders.CONNECTION, "close").build();
        return url -> client.get().uri(url).retrieve().body(String.class);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    @FunctionalInterface interface HttpFetcher { String get(String url); }
    @FunctionalInterface interface Sleeper { void sleep(long millis) throws InterruptedException; }
}
