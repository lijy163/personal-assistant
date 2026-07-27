package com.personal.assistant.module.gold.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.gold.dto.GoldApiConfigRequest;
import com.personal.assistant.module.gold.dto.GoldApiConfigResponse;
import com.personal.assistant.module.gold.dto.GoldQuoteRefreshResponse;
import com.personal.assistant.module.gold.dto.GoldQuoteStatusResponse;
import com.personal.assistant.module.gold.dto.GoldWatchRequest;
import com.personal.assistant.module.gold.entity.GoldApiConfig;
import com.personal.assistant.module.gold.entity.GoldCollectionResult;
import com.personal.assistant.module.gold.entity.GoldWatchItem;
import com.personal.assistant.module.gold.mapper.GoldApiConfigMapper;
import com.personal.assistant.module.gold.mapper.GoldCollectionResultMapper;
import com.personal.assistant.module.gold.mapper.GoldWatchItemMapper;
import com.personal.assistant.module.reminder.service.SecretCryptoService;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GoldService {
    private static final Set<String> GOLD_TYPES = Set.of("LONDON_GOLD", "DOMESTIC_GOLD", "BRAND_JEWELRY", "PLATFORM_GOLD");
    private static final Set<String> AUTH_TYPES = Set.of("NONE", "BEARER", "QUERY_KEY");

    private final GoldWatchItemMapper watches;
    private final GoldApiConfigMapper configs;
    private final GoldCollectionResultMapper results;
    private final SecretCryptoService crypto;
    private final RestClient client = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GoldService(GoldWatchItemMapper watches,
                       GoldApiConfigMapper configs,
                       GoldCollectionResultMapper results,
                       SecretCryptoService crypto) {
        this.watches = watches;
        this.configs = configs;
        this.results = results;
        this.crypto = crypto;
    }

    public List<GoldWatchItem> listWatches(Long uid, String keyword, String goldType, Boolean enabled) {
        return watches.selectList(new LambdaQueryWrapper<GoldWatchItem>()
                .eq(GoldWatchItem::getUserId, uid)
                .and(StringUtils.hasText(keyword), q -> q.like(GoldWatchItem::getDisplayName, keyword)
                        .or().like(GoldWatchItem::getBrandName, keyword)
                        .or().like(GoldWatchItem::getSourceName, keyword))
                .eq(StringUtils.hasText(goldType), GoldWatchItem::getGoldType, goldType)
                .eq(enabled != null, GoldWatchItem::getEnabled, enabled)
                .orderByAsc(GoldWatchItem::getGoldType)
                .orderByAsc(GoldWatchItem::getDisplayName));
    }

    public GoldQuoteStatusResponse quoteStatus(Long uid, String goldType, boolean enabledOnly) {
        List<GoldWatchItem> items = watches.selectList(new LambdaQueryWrapper<GoldWatchItem>()
                .eq(GoldWatchItem::getUserId, uid)
                .eq(StringUtils.hasText(goldType), GoldWatchItem::getGoldType, goldType)
                .eq(enabledOnly, GoldWatchItem::getEnabled, true)
                .orderByAsc(GoldWatchItem::getGoldType)
                .orderByAsc(GoldWatchItem::getDisplayName));
        List<Long> ids = items.stream().map(GoldWatchItem::getId).toList();
        int quotedCount = (int) items.stream().filter(item -> item.getLatestPrice() != null && item.getQuoteTime() != null).count();
        LocalDateTime lastQuoteTime = items.stream()
                .map(GoldWatchItem::getQuoteTime)
                .filter(t -> t != null)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        if (ids.isEmpty()) {
            return new GoldQuoteStatusResponse(0, 0, 0, null, null, 0, 0, List.of());
        }
        List<GoldCollectionResult> recent = results.selectList(new LambdaQueryWrapper<GoldCollectionResult>()
                .in(GoldCollectionResult::getWatchItemId, ids)
                .orderByDesc(GoldCollectionResult::getCollectedAt)
                .last("limit 50"));
        LocalDateTime lastRefreshTime = recent.stream()
                .map(GoldCollectionResult::getCollectedAt)
                .filter(t -> t != null)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        int recentSuccess = (int) recent.stream().filter(r -> Boolean.TRUE.equals(r.getSuccess())).count();
        int recentFailed = (int) recent.stream().filter(r -> Boolean.FALSE.equals(r.getSuccess())).count();
        Map<Long, GoldWatchItem> itemMap = items.stream().collect(Collectors.toMap(GoldWatchItem::getId, item -> item));
        List<GoldQuoteStatusResponse.GoldQuoteFailure> failures = recent.stream()
                .filter(r -> Boolean.FALSE.equals(r.getSuccess()))
                .limit(5)
                .map(r -> {
                    GoldWatchItem item = itemMap.get(r.getWatchItemId());
                    return new GoldQuoteStatusResponse.GoldQuoteFailure(
                            r.getWatchItemId(),
                            item == null ? "#" + r.getWatchItemId() : item.getDisplayName(),
                            r.getErrorMessage(),
                            r.getCollectedAt()
                    );
                })
                .toList();
        return new GoldQuoteStatusResponse(
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
    public Long saveWatch(Long uid, Long id, GoldWatchRequest request) {
        if (!GOLD_TYPES.contains(request.goldType())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Unsupported gold type");
        }
        GoldWatchItem item = id == null ? new GoldWatchItem() : requireWatch(uid, id);
        item.setUserId(uid);
        item.setGoldType(request.goldType());
        item.setBrandName(trimToNull(request.brandName()));
        item.setDisplayName(request.displayName().trim());
        item.setUnit(request.unit().trim());
        item.setLatestPrice(request.latestPrice());
        item.setChangeAmount(request.changeAmount());
        item.setChangePercent(request.changePercent());
        item.setHighPrice(request.highPrice());
        item.setLowPrice(request.lowPrice());
        item.setOpenPrice(request.openPrice());
        item.setPreviousClose(request.previousClose());
        item.setBuyPrice(request.buyPrice());
        item.setSellPrice(request.sellPrice());
        item.setQuoteTime(request.quoteTime());
        item.setSourceName(trimToNull(request.sourceName()));
        item.setSourceUrl(trimToNull(request.sourceUrl()));
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
        GoldWatchItem item = requireWatch(uid, id);
        item.setEnabled(enabled);
        item.setUpdatedAt(LocalDateTime.now());
        watches.updateById(item);
    }

    public List<GoldApiConfigResponse> listConfigs(Long uid) {
        return configs.selectList(new LambdaQueryWrapper<GoldApiConfig>().eq(GoldApiConfig::getUserId, uid))
                .stream().map(this::response).toList();
    }

    @Transactional
    public Long saveConfig(Long uid, Long id, GoldApiConfigRequest request) {
        if (!AUTH_TYPES.contains(request.authType())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Unsupported auth type");
        }
        GoldApiConfig config = id == null ? new GoldApiConfig() : requireConfig(uid, id);
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
        GoldApiConfig config = requireConfig(uid, id);
        boolean ok = false;
        String message;
        try {
            String raw = request(config, "TEST", "", "CNY/G");
            ok = StringUtils.hasText(raw);
            message = ok ? "Connection OK" : "Empty response";
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

    public List<GoldCollectionResult> listResults(Long uid, Long watchId) {
        List<Long> ids = listWatches(uid, null, null, null).stream().map(GoldWatchItem::getId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return results.selectList(new LambdaQueryWrapper<GoldCollectionResult>()
                .in(GoldCollectionResult::getWatchItemId, ids)
                .eq(watchId != null, GoldCollectionResult::getWatchItemId, watchId)
                .orderByDesc(GoldCollectionResult::getCollectedAt)
                .last("limit 200"));
    }

    @Transactional
    public GoldQuoteRefreshResponse refreshQuotes(Long uid, String goldType, boolean enabledOnly) {
        List<GoldWatchItem> items = watches.selectList(new LambdaQueryWrapper<GoldWatchItem>()
                .eq(GoldWatchItem::getUserId, uid)
                .eq(StringUtils.hasText(goldType), GoldWatchItem::getGoldType, goldType)
                .eq(enabledOnly, GoldWatchItem::getEnabled, true)
                .orderByAsc(GoldWatchItem::getGoldType)
                .orderByAsc(GoldWatchItem::getDisplayName));
        return refreshQuoteItems(items, firstEnabledConfig(uid));
    }

    @Transactional
    public GoldQuoteRefreshResponse refreshEnabledQuotesForScheduler() {
        List<GoldWatchItem> items = watches.selectList(new LambdaQueryWrapper<GoldWatchItem>()
                .eq(GoldWatchItem::getEnabled, true)
                .orderByAsc(GoldWatchItem::getGoldType)
                .orderByAsc(GoldWatchItem::getDisplayName));
        return refreshQuoteItems(items, firstEnabledConfig(null));
    }

    public int collectEnabled() {
        return refreshEnabledQuotesForScheduler().total();
    }

    private GoldQuoteRefreshResponse refreshQuoteItems(List<GoldWatchItem> items, GoldApiConfig config) {
        List<GoldQuoteRefreshResponse.GoldQuoteRefreshItem> refreshItems = new ArrayList<>();
        int success = 0;
        int failed = 0;
        for (GoldWatchItem item : items) {
            GoldCollectionResult result = new GoldCollectionResult();
            result.setWatchItemId(item.getId());
            result.setApiConfigId(config == null ? null : config.getId());
            result.setCollectedAt(LocalDateTime.now());
            try {
                if (config == null) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Not configured可用金价接口，请先在接口配置中新增数据源，或手动维护价格");
                }
                String raw = request(config, item.getGoldType(), item.getBrandName(), item.getUnit());
                GoldQuote quote = parseQuote(raw);
                applyQuote(item, quote);
                watches.updateById(item);

                result.setSuccess(true);
                result.setSummary("Gold quote refreshed: latest price " + item.getLatestPrice() + " " + item.getUnit());
                result.setRawData(limit(raw, 10000));
                refreshItems.add(new GoldQuoteRefreshResponse.GoldQuoteRefreshItem(
                        item.getId(), item.getDisplayName(), item.getGoldType(), true, "Refreshed"));
                success++;
            } catch (Exception e) {
                result.setSuccess(false);
                result.setErrorMessage(e.getMessage());
                refreshItems.add(new GoldQuoteRefreshResponse.GoldQuoteRefreshItem(
                        item.getId(), item.getDisplayName(), item.getGoldType(), false, e.getMessage()));
                failed++;
            }
            results.insert(result);
        }
        return new GoldQuoteRefreshResponse(items.size(), success, failed, LocalDateTime.now(), refreshItems);
    }

    private GoldApiConfig firstEnabledConfig(Long uid) {
        List<GoldApiConfig> enabled = configs.selectList(new LambdaQueryWrapper<GoldApiConfig>()
                .eq(uid != null, GoldApiConfig::getUserId, uid)
                .eq(GoldApiConfig::getEnabled, true)
                .orderByAsc(GoldApiConfig::getId)
                .last("limit 1"));
        return enabled.isEmpty() ? null : enabled.get(0);
    }

    private void applyQuote(GoldWatchItem item, GoldQuote quote) {
        item.setLatestPrice(firstNonNull(quote.latestPrice(), item.getLatestPrice()));
        item.setChangeAmount(firstNonNull(quote.changeAmount(), item.getChangeAmount()));
        item.setChangePercent(firstNonNull(quote.changePercent(), item.getChangePercent()));
        item.setHighPrice(firstNonNull(quote.highPrice(), item.getHighPrice()));
        item.setLowPrice(firstNonNull(quote.lowPrice(), item.getLowPrice()));
        item.setOpenPrice(firstNonNull(quote.openPrice(), item.getOpenPrice()));
        item.setPreviousClose(firstNonNull(quote.previousClose(), item.getPreviousClose()));
        item.setBuyPrice(firstNonNull(quote.buyPrice(), item.getBuyPrice()));
        item.setSellPrice(firstNonNull(quote.sellPrice(), item.getSellPrice()));
        item.setQuoteTime(firstNonNull(quote.quoteTime(), LocalDateTime.now()));
        item.setUpdatedAt(LocalDateTime.now());
    }

    private GoldQuote parseQuote(String raw) throws Exception {
        JsonNode root = objectMapper.readTree(raw);
        JsonNode node = quoteNode(root);
        BigDecimal latestPrice = decimal(node, "latestPrice", "price", "last", "close", "current", "value", "sellPrice");
        if (latestPrice == null) {
            throw new IllegalStateException("Latest price not found. Supported fields include price/latestPrice/current/value.");
        }
        return new GoldQuote(
                latestPrice,
                decimal(node, "changeAmount", "change", "riseFall", "diff"),
                decimal(node, "changePercent", "percent", "pctChange", "chgPct", "riseFallPercent"),
                decimal(node, "highPrice", "high", "highest"),
                decimal(node, "lowPrice", "low", "lowest"),
                decimal(node, "openPrice", "open", "openPrice"),
                decimal(node, "previousClose", "prevClose", "yesterdayClose", "lastClose"),
                decimal(node, "buyPrice", "buy", "bid"),
                decimal(node, "sellPrice", "sell", "ask"),
                time(node, "quoteTime", "time", "timestamp", "updatedAt")
        );
    }

    private JsonNode quoteNode(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) return objectMapper.createObjectNode();
        JsonNode data = root.path("data");
        if (data.isArray() && !data.isEmpty()) return data.get(0);
        if (data.isObject()) {
            JsonNode quote = data.path("quote");
            if (quote.isObject()) return quote;
            return data;
        }
        JsonNode quote = root.path("quote");
        if (quote.isObject()) return quote;
        if (root.isArray() && !root.isEmpty()) return root.get(0);
        return root;
    }

    private String request(GoldApiConfig config, String type, String brand, String unit) {
        String url = config.getEndpoint()
                .replace("{type}", encode(type == null ? "" : type))
                .replace("{goldType}", encode(type == null ? "" : type))
                .replace("{brand}", encode(brand == null ? "" : brand))
                .replace("{unit}", encode(unit == null ? "" : unit));
        String key = StringUtils.hasText(config.getApiKeyEncrypted()) ? crypto.decrypt(config.getApiKeyEncrypted()) : "";
        RestClient.RequestHeadersSpec<?> req;
        if ("QUERY_KEY".equals(config.getAuthType())) {
            url += url.contains("?") ? "&api_key=" + encode(key) : "?api_key=" + encode(key);
            req = client.get().uri(url);
        } else {
            var spec = client.get().uri(url).header(HttpHeaders.USER_AGENT, "Mozilla/5.0 PersonalAssistant/1.0");
            if ("BEARER".equals(config.getAuthType())) spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + key);
            req = spec;
        }
        return req.retrieve().body(String.class);
    }

    private GoldWatchItem requireWatch(Long uid, Long id) {
        GoldWatchItem item = watches.selectById(id);
        if (item == null || !uid.equals(item.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Gold watch item not found");
        }
        return item;
    }

    private GoldApiConfig requireConfig(Long uid, Long id) {
        GoldApiConfig config = configs.selectById(id);
        if (config == null || !uid.equals(config.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Gold API config not found");
        }
        return config;
    }

    private GoldApiConfigResponse response(GoldApiConfig config) {
        String key = StringUtils.hasText(config.getApiKeyEncrypted()) ? crypto.decrypt(config.getApiKeyEncrypted()) : "";
        String masked = key.isEmpty() ? "Not configured" : key.length() < 8 ? "******" : key.substring(0, 3) + "******" + key.substring(key.length() - 3);
        return new GoldApiConfigResponse(config.getId(), config.getApiName(), config.getPurpose(), config.getEndpoint(),
                config.getAuthType(), masked, config.getRateLimitPerMinute(), Boolean.TRUE.equals(config.getEnabled()),
                config.getLastTestTime(), config.getLastTestSuccess(), config.getLastTestMessage());
    }

    private BigDecimal decimal(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            BigDecimal decimal = rawDecimal(value);
            if (decimal != null) return decimal;
        }
        return null;
    }

    private BigDecimal rawDecimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        if (node.isNumber()) return node.decimalValue();
        if (node.isTextual()) {
            String text = node.asText().replace(",", "").replace("%", "").trim();
            if (!StringUtils.hasText(text) || "-".equals(text)) return null;
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private LocalDateTime time(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            LocalDateTime time = parseTime(value);
            if (time != null) return time;
        }
        return null;
    }

    private LocalDateTime parseTime(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        try {
            if (node.isNumber()) {
                long epoch = node.asLong();
                if (String.valueOf(epoch).length() == 13) epoch = epoch / 1000;
                return LocalDateTime.ofEpochSecond(epoch, 0, OffsetDateTime.now().getOffset());
            }
            if (node.isTextual() && StringUtils.hasText(node.asText())) {
                String text = node.asText().trim();
                try {
                    return LocalDateTime.parse(text);
                } catch (Exception ignored) {
                    return OffsetDateTime.parse(text).toLocalDateTime();
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private <T> T firstNonNull(T value, T fallback) {
        return value != null ? value : fallback;
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

    private record GoldQuote(
            BigDecimal latestPrice,
            BigDecimal changeAmount,
            BigDecimal changePercent,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal openPrice,
            BigDecimal previousClose,
            BigDecimal buyPrice,
            BigDecimal sellPrice,
            LocalDateTime quoteTime
    ) {
    }
}
