package com.personal.assistant.module.gold.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.gold.dto.GoldPublicQuoteResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class PublicGoldQuoteService {
    private static final String GOLD_URL = "https://api.gold-api.com/price/XAU";
    private static final String EXCHANGE_URL = "https://open.er-api.com/v6/latest/USD";
    private static final BigDecimal GRAMS_PER_TROY_OUNCE = new BigDecimal("31.1034768");
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private final RestClient client;
    private final ObjectMapper objectMapper;

    @Autowired
    public PublicGoldQuoteService(ObjectMapper objectMapper) {
        this(objectMapper, RestClient.builder().defaultHeader(HttpHeaders.USER_AGENT,
                "Mozilla/5.0 PersonalAssistant/1.0").build());
    }

    PublicGoldQuoteService(ObjectMapper objectMapper, RestClient client) {
        this.objectMapper = objectMapper;
        this.client = client;
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
            List<GoldPublicQuoteResponse.Quote> quotes = List.of(
                    new GoldPublicQuoteResponse.Quote("XAU_USD", "国际现货黄金",
                            usdPerOunce.setScale(2, RoundingMode.HALF_UP), "USD/盎司",
                            "国际现货黄金 XAU/USD 实时参考价", false),
                    new GoldPublicQuoteResponse.Quote("XAU_CNY_GRAM", "国际金折算人民币", cnyPerGram,
                            "元/克", "按实时 XAU/USD × USD/CNY ÷ 31.1034768 折算", true)
            );
            return new GoldPublicQuoteResponse(quotes, usdCny.setScale(4, RoundingMode.HALF_UP),
                    quoteTime == null ? fetchedAt : quoteTime, fetchedAt, "Gold API + ExchangeRate-API", 60);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTEGRATION_ERROR, "免配置金价源暂时不可用：" + exception.getMessage());
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

}
