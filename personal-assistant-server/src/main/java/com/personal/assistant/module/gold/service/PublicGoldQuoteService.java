package com.personal.assistant.module.gold.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.gold.dto.GoldPublicQuoteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class PublicGoldQuoteService {
    private static final Logger log = LoggerFactory.getLogger(PublicGoldQuoteService.class);
    private static final String GOLD_URL = "https://api.gold-api.com/price/XAU";
    private static final String EXCHANGE_URL = "https://open.er-api.com/v6/latest/USD";
    private static final String JEWELRY_URL = "https://api.t1qq.com/api/v1/tool/rate/goldQuotation?key=";
    private static final BigDecimal GRAMS_PER_TROY_OUNCE = new BigDecimal("31.1034768");
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final String jewelryApiKey;

    @Autowired
    public PublicGoldQuoteService(ObjectMapper objectMapper,
                                  @Value("${T1QQ_API_KEY:${JEWELRY_GOLD_API_KEY:}}") String jewelryApiKey) {
        this(objectMapper, RestClient.builder().defaultHeader(HttpHeaders.USER_AGENT,
                "Mozilla/5.0 PersonalAssistant/1.0").build(), jewelryApiKey);
    }

    PublicGoldQuoteService(ObjectMapper objectMapper, RestClient client, String jewelryApiKey) {
        this.objectMapper = objectMapper;
        this.client = client;
        this.jewelryApiKey = jewelryApiKey;
    }

    public GoldPublicQuoteResponse latest() {
        try {
            JsonNode gold = get(GOLD_URL);
            JsonNode exchange = get(EXCHANGE_URL);
            BigDecimal usdPerOunce = requiredDecimal(gold.path("price"), "国际金价");
            BigDecimal usdCny = requiredDecimal(exchange.path("rates").path("CNY"), "美元人民币汇率");
            BigDecimal cnyPerGram = usdPerOunce.multiply(usdCny)
                    .divide(GRAMS_PER_TROY_OUNCE, 4, RoundingMode.HALF_UP);
            LocalDateTime quoteTime = parseTime(gold.path("updatedAt").asText(null));
            LocalDateTime fetchedAt = LocalDateTime.now(SHANGHAI);
            List<GoldPublicQuoteResponse.Quote> quotes = new ArrayList<>();
            quotes.add(new GoldPublicQuoteResponse.Quote("XAU_USD", "国际现货黄金",
                    usdPerOunce.setScale(2, RoundingMode.HALF_UP), "USD/盎司",
                    "国际现货黄金 XAU/USD 实时参考价", false));
            quotes.add(new GoldPublicQuoteResponse.Quote("XAU_CNY_GRAM", "国际金折算人民币", cnyPerGram,
                    "元/克", "按实时 XAU/USD × USD/CNY ÷ 31.1034768 折算，不代表品牌零售价", true));

            JewelryLoadResult jewelry = appendJewelryQuotes(quotes);
            String source = jewelry.loaded() ? "Gold API + ExchangeRate-API + 应天API" : "Gold API + ExchangeRate-API";
            return new GoldPublicQuoteResponse(quotes, usdCny.setScale(4, RoundingMode.HALF_UP),
                    quoteTime == null ? fetchedAt : quoteTime, fetchedAt, source, 60,
                    jewelry.configured(), jewelry.loaded(), jewelry.message());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTEGRATION_ERROR, "免配置金价源暂时不可用：" + exception.getMessage());
        }
    }

    private JewelryLoadResult appendJewelryQuotes(List<GoldPublicQuoteResponse.Quote> quotes) {
        if (jewelryApiKey == null || jewelryApiKey.isBlank()) {
            return new JewelryLoadResult(false, false, "服务器未配置 T1QQ_API_KEY，无法查询首饰金价");
        }
        try {
            String key = URLEncoder.encode(jewelryApiKey, StandardCharsets.UTF_8);
            JsonNode response = get(JEWELRY_URL + key);
            if (response.path("code").asInt() != 200) {
                String message = response.path("msg").asText("接口返回失败");
                log.warn("应天首饰金价接口返回失败: {}", message);
                return new JewelryLoadResult(true, false, "应天API调用失败：" + message);
            }
            JsonNode prices = response.path("data").path("gold_prices");
            if (!prices.isArray()) {
                log.warn("应天首饰金价响应缺少 data.gold_prices 数组");
                return new JewelryLoadResult(true, false, "应天API响应格式异常，未找到品牌报价列表");
            }
            int loaded = 0;
            for (JsonNode item : prices) {
                String brand = item.path("brand").asText("").trim();
                BigDecimal price = decimal(item.path("gold_price"));
                if (brand.isEmpty() || price == null) continue;
                String unit = item.path("unit").asText("元/克");
                String updateDate = item.path("update_date").asText("");
                quotes.add(new GoldPublicQuoteResponse.Quote("JEWELRY_" + brand, brand + "首饰金", price,
                        unit, "品牌黄金首饰零售参考价" + (updateDate.isEmpty() ? "" : "，更新于 " + updateDate), false));
                loaded++;
            }
            return loaded > 0
                    ? new JewelryLoadResult(true, true, "已加载 " + loaded + " 个品牌首饰金价")
                    : new JewelryLoadResult(true, false, "应天API未返回有效的品牌黄金价格");
        } catch (Exception exception) {
            log.warn("应天首饰金价接口调用异常", exception);
            return new JewelryLoadResult(true, false, "应天API连接失败：" + safeMessage(exception));
        }
    }

    private JsonNode get(String url) throws Exception {
        String body = client.get().uri(url).retrieve().body(String.class);
        return objectMapper.readTree(body);
    }

    private BigDecimal requiredDecimal(JsonNode value, String name) {
        if (value == null || !value.isNumber()) {
            throw new BusinessException(ErrorCode.INTEGRATION_ERROR, name + "响应缺少有效价格");
        }
        return value.decimalValue();
    }

    private BigDecimal decimal(JsonNode value) {
        if (value == null || value.isNull()) return null;
        String text = value.asText("").replace(",", "").trim();
        if (text.isEmpty() || "-".equals(text)) return null;
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(SHANGHAI).toLocalDateTime();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.ofInstant(Instant.parse(value), SHANGHAI);
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private record JewelryLoadResult(boolean configured, boolean loaded, String message) {
    }
}
