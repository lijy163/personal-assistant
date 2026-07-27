package com.personal.assistant.module.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.reminder.service.SecretCryptoService;
import com.personal.assistant.module.stock.dto.StockApiConfigRequest;
import com.personal.assistant.module.stock.dto.StockApiConfigResponse;
import com.personal.assistant.module.stock.dto.StockMarketMapResponse;
import com.personal.assistant.module.stock.dto.StockQuoteRefreshResponse;
import com.personal.assistant.module.stock.dto.StockQuoteStatusResponse;
import com.personal.assistant.module.stock.dto.StockWatchRequest;
import com.personal.assistant.module.stock.entity.StockApiConfig;
import com.personal.assistant.module.stock.entity.StockCollectionResult;
import com.personal.assistant.module.stock.entity.StockWatchItem;
import com.personal.assistant.module.stock.mapper.StockApiConfigMapper;
import com.personal.assistant.module.stock.mapper.StockCollectionResultMapper;
import com.personal.assistant.module.stock.mapper.StockWatchItemMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StockService {
    private static final Set<String> MARKETS = Set.of("CN", "US", "HK");
    private static final Set<String> AUTH_TYPES = Set.of("NONE", "BEARER", "QUERY_KEY");
    private static final BigDecimal DEFAULT_WEIGHT = BigDecimal.valueOf(100);
    private static final String EASTMONEY_QUOTE_URL = "https://push2.eastmoney.com/api/qt/stock/get?secid=%s&fields=f43,f57,f58,f116,f170,f152";

    private final StockWatchItemMapper watches;
    private final StockApiConfigMapper configs;
    private final StockCollectionResultMapper results;
    private final SecretCryptoService crypto;
    private final RestClient client = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StockService(StockWatchItemMapper watches,
                        StockApiConfigMapper configs,
                        StockCollectionResultMapper results,
                        SecretCryptoService crypto) {
        this.watches = watches;
        this.configs = configs;
        this.results = results;
        this.crypto = crypto;
    }

    public List<StockWatchItem> listWatches(Long uid, String keyword, String market, String tag, Boolean enabled) {
        return watches.selectList(new LambdaQueryWrapper<StockWatchItem>()
                .eq(StockWatchItem::getUserId, uid)
                .and(StringUtils.hasText(keyword), q -> q.like(StockWatchItem::getStockCode, keyword)
                        .or().like(StockWatchItem::getStockName, keyword))
                .eq(StringUtils.hasText(market), StockWatchItem::getMarket, market)
                .like(StringUtils.hasText(tag), StockWatchItem::getTags, tag)
                .eq(enabled != null, StockWatchItem::getEnabled, enabled)
                .orderByAsc(StockWatchItem::getMarket)
                .orderByAsc(StockWatchItem::getStockCode));
    }

    public StockMarketMapResponse marketMap(Long uid, String market, boolean enabledOnly) {
        List<StockWatchItem> items = watches.selectList(new LambdaQueryWrapper<StockWatchItem>()
                .eq(StockWatchItem::getUserId, uid)
                .eq(StringUtils.hasText(market), StockWatchItem::getMarket, market)
                .eq(enabledOnly, StockWatchItem::getEnabled, true)
                .orderByAsc(StockWatchItem::getMarket)
                .orderByAsc(StockWatchItem::getIndustry)
                .orderByAsc(StockWatchItem::getStockCode));

        List<StockMarketMapResponse.StockMapItem> mapItems = items.stream().map(this::toMapItem).toList();
        Map<String, List<StockMarketMapResponse.StockMapItem>> grouped = mapItems.stream()
                .collect(Collectors.groupingBy(item -> normalizeIndustry(item.industry()), LinkedHashMap::new, Collectors.toList()));

        List<StockMarketMapResponse.StockIndustryNode> industries = new ArrayList<>();
        for (Map.Entry<String, List<StockMarketMapResponse.StockMapItem>> entry : grouped.entrySet()) {
            List<StockMarketMapResponse.StockMapItem> children = entry.getValue().stream()
                    .sorted(Comparator.comparing(StockMarketMapResponse.StockMapItem::weight).reversed())
                    .toList();
            industries.add(new StockMarketMapResponse.StockIndustryNode(
                    entry.getKey(),
                    children.size(),
                    averageChange(children),
                    sumMarketValue(children),
                    children
            ));
        }
        industries.sort(Comparator.comparing(StockMarketMapResponse.StockIndustryNode::totalMarketValue,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return new StockMarketMapResponse(
                StringUtils.hasText(market) ? market : "ALL",
                enabledOnly ? "WATCH_ENABLED" : "WATCH_ALL",
                LocalDateTime.now(),
                stats(mapItems),
                industries
        );
    }

    public StockQuoteStatusResponse quoteStatus(Long uid, String market, boolean enabledOnly) {
        List<StockWatchItem> items = watches.selectList(new LambdaQueryWrapper<StockWatchItem>()
                .eq(StockWatchItem::getUserId, uid)
                .eq(StringUtils.hasText(market), StockWatchItem::getMarket, market)
                .eq(enabledOnly, StockWatchItem::getEnabled, true)
                .orderByAsc(StockWatchItem::getMarket)
                .orderByAsc(StockWatchItem::getStockCode));
        List<Long> ids = items.stream().map(StockWatchItem::getId).toList();
        int quotedCount = (int) items.stream().filter(item -> item.getLatestPrice() != null && item.getQuoteTime() != null).count();
        LocalDateTime lastQuoteTime = items.stream()
                .map(StockWatchItem::getQuoteTime)
                .filter(t -> t != null)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        if (ids.isEmpty()) {
            return new StockQuoteStatusResponse(0, 0, 0, null, null, 0, 0, List.of());
        }
        List<StockCollectionResult> recent = results.selectList(new LambdaQueryWrapper<StockCollectionResult>()
                .in(StockCollectionResult::getWatchItemId, ids)
                .orderByDesc(StockCollectionResult::getCollectedAt)
                .last("limit 50"));
        LocalDateTime lastRefreshTime = recent.stream()
                .map(StockCollectionResult::getCollectedAt)
                .filter(t -> t != null)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        int recentSuccess = (int) recent.stream().filter(r -> Boolean.TRUE.equals(r.getSuccess())).count();
        int recentFailed = (int) recent.stream().filter(r -> Boolean.FALSE.equals(r.getSuccess())).count();
        Map<Long, StockWatchItem> itemMap = items.stream().collect(Collectors.toMap(StockWatchItem::getId, item -> item));
        List<StockQuoteStatusResponse.StockQuoteFailure> failures = recent.stream()
                .filter(r -> Boolean.FALSE.equals(r.getSuccess()))
                .limit(5)
                .map(r -> {
                    StockWatchItem item = itemMap.get(r.getWatchItemId());
                    return new StockQuoteStatusResponse.StockQuoteFailure(
                            r.getWatchItemId(),
                            item == null ? "#" + r.getWatchItemId() : item.getStockName(),
                            r.getErrorMessage(),
                            r.getCollectedAt()
                    );
                })
                .toList();
        return new StockQuoteStatusResponse(
                items.size(),
                quotedCount,
                items.size() - quotedCount,
                lastQuoteTime,
                lastRefreshTime,
                recentSuccess,
                recentFailed,
                failures
        );
    }

    @Transactional
    public StockQuoteRefreshResponse refreshQuotes(Long uid, String market, boolean enabledOnly) {
        List<StockWatchItem> items = watches.selectList(new LambdaQueryWrapper<StockWatchItem>()
                .eq(StockWatchItem::getUserId, uid)
                .eq(StringUtils.hasText(market), StockWatchItem::getMarket, market)
                .eq(enabledOnly, StockWatchItem::getEnabled, true)
                .orderByAsc(StockWatchItem::getMarket)
                .orderByAsc(StockWatchItem::getStockCode));
        return refreshQuoteItems(items);
    }

    @Transactional
    public StockQuoteRefreshResponse refreshEnabledQuotesForScheduler() {
        List<StockWatchItem> items = watches.selectList(new LambdaQueryWrapper<StockWatchItem>()
                .eq(StockWatchItem::getEnabled, true)
                .orderByAsc(StockWatchItem::getMarket)
                .orderByAsc(StockWatchItem::getStockCode));
        return refreshQuoteItems(items);
    }

    @Transactional
    public Long saveWatch(Long uid, Long id, StockWatchRequest request) {
        if (!MARKETS.contains(request.market())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "市场类型不合法");
        }
        StockWatchItem item = id == null ? new StockWatchItem() : requireWatch(uid, id);
        item.setUserId(uid);
        item.setStockCode(request.stockCode().trim().toUpperCase());
        item.setStockName(request.stockName().trim());
        item.setMarket(request.market());
        item.setIndustry(trimToNull(request.industry()));
        item.setLatestPrice(request.latestPrice());
        item.setChangePercent(request.changePercent());
        item.setMarketValue(request.marketValue());
        item.setQuoteTime(request.quoteTime());
        item.setTags(request.tags());
        item.setReason(request.reason());
        item.setRemark(request.remark());
        item.setEnabled(!Boolean.FALSE.equals(request.enabled()));
        item.setUpdatedAt(LocalDateTime.now());
        if (id == null) {
            item.setCreatedAt(LocalDateTime.now());
            watches.insert(item);
        } else {
            watches.updateById(item);
        }
        return item.getId();
    }

    @Transactional
    public void toggleWatch(Long uid, Long id, boolean enabled) {
        StockWatchItem item = requireWatch(uid, id);
        item.setEnabled(enabled);
        item.setUpdatedAt(LocalDateTime.now());
        watches.updateById(item);
    }

    public List<StockApiConfigResponse> listConfigs(Long uid) {
        return configs.selectList(new LambdaQueryWrapper<StockApiConfig>().eq(StockApiConfig::getUserId, uid))
                .stream().map(this::response).toList();
    }

    @Transactional
    public Long saveConfig(Long uid, Long id, StockApiConfigRequest request) {
        if (!AUTH_TYPES.contains(request.authType())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "认证类型不合法");
        }
        StockApiConfig config = id == null ? new StockApiConfig() : requireConfig(uid, id);
        config.setUserId(uid);
        config.setApiName(request.apiName());
        config.setPurpose(request.purpose());
        config.setEndpoint(request.endpoint());
        config.setAuthType(request.authType());
        if (StringUtils.hasText(request.apiKey())) {
            config.setApiKeyEncrypted(crypto.encrypt(request.apiKey()));
        }
        config.setRateLimitPerMinute(request.rateLimitPerMinute());
        config.setEnabled(!Boolean.FALSE.equals(request.enabled()));
        config.setUpdatedAt(LocalDateTime.now());
        if (id == null) {
            config.setCreatedAt(LocalDateTime.now());
            configs.insert(config);
        } else {
            configs.updateById(config);
        }
        return config.getId();
    }

    @Transactional
    public boolean testConfig(Long uid, Long id) {
        StockApiConfig config = requireConfig(uid, id);
        boolean ok = false;
        String message;
        try {
            request(config, "TEST", "US");
            ok = true;
            message = "接口连接成功";
        } catch (Exception e) {
            message = e.getMessage();
        }
        config.setLastTestTime(LocalDateTime.now());
        config.setLastTestSuccess(ok);
        config.setLastTestMessage(message);
        config.setUpdatedAt(LocalDateTime.now());
        configs.updateById(config);
        return ok;
    }

    public List<StockCollectionResult> listResults(Long uid, Long watchId) {
        List<Long> ids = listWatches(uid, null, null, null, null).stream().map(StockWatchItem::getId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return results.selectList(new LambdaQueryWrapper<StockCollectionResult>()
                .in(StockCollectionResult::getWatchItemId, ids)
                .eq(watchId != null, StockCollectionResult::getWatchItemId, watchId)
                .orderByDesc(StockCollectionResult::getCollectedAt)
                .last("limit 200"));
    }

    public int collectEnabled() {
        return refreshEnabledQuotesForScheduler().total();
    }

    private StockQuoteRefreshResponse refreshQuoteItems(List<StockWatchItem> items) {
        List<StockQuoteRefreshResponse.StockQuoteRefreshItem> refreshItems = new ArrayList<>();
        int success = 0;
        int failed = 0;
        for (StockWatchItem item : items) {
            StockCollectionResult result = new StockCollectionResult();
            result.setWatchItemId(item.getId());
            result.setCollectedAt(LocalDateTime.now());
            try {
                RealtimeQuote quote = fetchRealtimeQuote(item);
                item.setLatestPrice(quote.latestPrice());
                item.setChangePercent(quote.changePercent());
                item.setMarketValue(quote.marketValue());
                item.setQuoteTime(quote.quoteTime());
                item.setUpdatedAt(LocalDateTime.now());
                watches.updateById(item);

                result.setSuccess(true);
                result.setSummary("实时行情刷新成功：最新价 " + quote.latestPrice() + "，涨跌幅 " + quote.changePercent() + "%");
                result.setRawData(quote.rawData());
                refreshItems.add(new StockQuoteRefreshResponse.StockQuoteRefreshItem(
                        item.getId(), item.getStockCode(), item.getStockName(), true, "刷新成功"));
                success++;
            } catch (Exception e) {
                result.setSuccess(false);
                result.setErrorMessage(e.getMessage());
                refreshItems.add(new StockQuoteRefreshResponse.StockQuoteRefreshItem(
                        item.getId(), item.getStockCode(), item.getStockName(), false, e.getMessage()));
                failed++;
            }
            results.insert(result);
        }
        return new StockQuoteRefreshResponse(items.size(), success, failed, LocalDateTime.now(), refreshItems);
    }

    private RealtimeQuote fetchRealtimeQuote(StockWatchItem item) throws Exception {
        if (!"CN".equals(item.getMarket())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前内置实时行情仅支持 A 股，港股/美股请后续配置供应商接口");
        }
        String secid = eastMoneySecid(item.getStockCode());
        if (!StringUtils.hasText(secid)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "无法识别 A 股代码所属交易所：" + item.getStockCode());
        }
        String url = EASTMONEY_QUOTE_URL.formatted(secid);
        String raw = client.get()
                .uri(url)
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 PersonalAssistant/1.0")
                .retrieve()
                .body(String.class);
        JsonNode data = objectMapper.readTree(raw).path("data");
        if (data.isMissingNode() || data.isNull()) {
            throw new IllegalStateException("行情接口未返回有效数据");
        }
        BigDecimal latestPrice = scaledDecimal(data.path("f43"), intValue(data.path("f152"), 2));
        BigDecimal changePercent = scaledDecimal(data.path("f170"), 2);
        BigDecimal marketValue = rawDecimal(data.path("f116"));
        if (latestPrice == null || changePercent == null) {
            throw new IllegalStateException("行情接口缺少最新价或涨跌幅字段");
        }
        return new RealtimeQuote(latestPrice, changePercent, marketValue, LocalDateTime.now(), limit(raw, 10000));
    }

    private String eastMoneySecid(String stockCode) {
        String code = stockCode == null ? "" : stockCode.trim().toUpperCase();
        if (code.startsWith("SH")) return "1." + code.substring(2);
        if (code.startsWith("SZ")) return "0." + code.substring(2);
        if (code.matches("6\\d{5}")) return "1." + code;
        if (code.matches("[023]\\d{5}")) return "0." + code;
        if (code.matches("[48]\\d{5}")) return "0." + code;
        return null;
    }

    private BigDecimal scaledDecimal(JsonNode node, int scale) {
        BigDecimal raw = rawDecimal(node);
        if (raw == null) return null;
        return raw.divide(BigDecimal.TEN.pow(scale), scale, RoundingMode.HALF_UP);
    }

    private BigDecimal rawDecimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        if (node.isTextual() && ("-".equals(node.asText()) || !StringUtils.hasText(node.asText()))) return null;
        try {
            return new BigDecimal(node.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int intValue(JsonNode node, int defaultValue) {
        if (node == null || node.isMissingNode() || node.isNull()) return defaultValue;
        return node.asInt(defaultValue);
    }

    private StockMarketMapResponse.StockMapItem toMapItem(StockWatchItem item) {
        BigDecimal weight = item.getMarketValue();
        if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
            weight = DEFAULT_WEIGHT;
        }
        return new StockMarketMapResponse.StockMapItem(
                item.getId(), item.getStockCode(), item.getStockName(), item.getMarket(), normalizeIndustry(item.getIndustry()),
                item.getLatestPrice(), item.getChangePercent(), item.getMarketValue(), weight, item.getQuoteTime(), item.getTags());
    }

    private StockMarketMapResponse.StockMarketMapStats stats(List<StockMarketMapResponse.StockMapItem> items) {
        int up = 0;
        int down = 0;
        int flat = 0;
        BigDecimal totalChange = BigDecimal.ZERO;
        int changeCount = 0;
        for (StockMarketMapResponse.StockMapItem item : items) {
            BigDecimal change = item.changePercent();
            if (change == null) {
                flat++;
                continue;
            }
            totalChange = totalChange.add(change);
            changeCount++;
            int sign = change.compareTo(BigDecimal.ZERO);
            if (sign > 0) up++;
            else if (sign < 0) down++;
            else flat++;
        }
        BigDecimal average = changeCount == 0 ? BigDecimal.ZERO : totalChange.divide(BigDecimal.valueOf(changeCount), 4, RoundingMode.HALF_UP);
        return new StockMarketMapResponse.StockMarketMapStats(items.size(), up, flat, down, average);
    }

    private BigDecimal averageChange(List<StockMarketMapResponse.StockMapItem> items) {
        List<BigDecimal> changes = items.stream().map(StockMarketMapResponse.StockMapItem::changePercent).filter(v -> v != null).toList();
        if (changes.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = changes.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(changes.size()), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal sumMarketValue(List<StockMarketMapResponse.StockMapItem> items) {
        BigDecimal sum = BigDecimal.ZERO;
        for (StockMarketMapResponse.StockMapItem item : items) {
            sum = sum.add(item.weight() == null ? DEFAULT_WEIGHT : item.weight());
        }
        return sum;
    }

    private String request(StockApiConfig config, String code, String market) {
        String url = config.getEndpoint().replace("{code}", encode(code)).replace("{market}", encode(market));
        String key = StringUtils.hasText(config.getApiKeyEncrypted()) ? crypto.decrypt(config.getApiKeyEncrypted()) : "";
        RestClient.RequestHeadersSpec<?> req;
        if ("QUERY_KEY".equals(config.getAuthType())) {
            url += url.contains("?") ? "&api_key=" + encode(key) : "?api_key=" + encode(key);
            req = client.get().uri(url);
        } else {
            var spec = client.get().uri(url);
            if ("BEARER".equals(config.getAuthType())) spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + key);
            req = spec;
        }
        return req.retrieve().body(String.class);
    }

    private StockWatchItem requireWatch(Long uid, Long id) {
        StockWatchItem item = watches.selectById(id);
        if (item == null || !uid.equals(item.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "股票关注项不存在");
        }
        return item;
    }

    private StockApiConfig requireConfig(Long uid, Long id) {
        StockApiConfig config = configs.selectById(id);
        if (config == null || !uid.equals(config.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "股票接口配置不存在");
        }
        return config;
    }

    private StockApiConfigResponse response(StockApiConfig config) {
        String key = StringUtils.hasText(config.getApiKeyEncrypted()) ? crypto.decrypt(config.getApiKeyEncrypted()) : "";
        String masked = key.isEmpty() ? "未配置" : key.length() < 8 ? "******" : key.substring(0, 3) + "******" + key.substring(key.length() - 3);
        return new StockApiConfigResponse(config.getId(), config.getApiName(), config.getPurpose(), config.getEndpoint(),
                config.getAuthType(), masked, config.getRateLimitPerMinute(), Boolean.TRUE.equals(config.getEnabled()),
                config.getLastTestTime(), config.getLastTestSuccess(), config.getLastTestMessage());
    }

    private String normalizeIndustry(String industry) {
        return StringUtils.hasText(industry) ? industry.trim() : "未分组";
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String limit(String value, int length) {
        if (value == null) return "";
        return value.length() > length ? value.substring(0, length) : value;
    }

    private record RealtimeQuote(BigDecimal latestPrice, BigDecimal changePercent, BigDecimal marketValue, LocalDateTime quoteTime, String rawData) {
    }
}