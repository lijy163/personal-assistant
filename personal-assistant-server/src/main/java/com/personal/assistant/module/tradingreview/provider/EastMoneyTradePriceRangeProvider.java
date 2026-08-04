package com.personal.assistant.module.tradingreview.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Component
public class EastMoneyTradePriceRangeProvider implements TradePriceRangeProvider {
    private static final String URL = "https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=%s&klt=101&fqt=1&beg=%s&end=%s&fields1=f1,f2,f3,f4,f5,f6&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61";
    private final RestClient client = RestClient.builder().defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 personal-assistant").build();
    private final ObjectMapper json = new ObjectMapper();

    @Override
    public Optional<PriceRange> range(String stockCode, LocalDate start, LocalDate end) {
        try {
            String secid = secid(stockCode);
            if (!StringUtils.hasText(secid) || start == null || end == null) return Optional.empty();
            String raw = client.get().uri(URL.formatted(secid, fmt(start), fmt(end))).retrieve().body(String.class);
            JsonNode rows = json.readTree(raw).path("data").path("klines");
            BigDecimal high = null, low = null;
            if (rows.isArray()) {
                for (JsonNode row : rows) {
                    String[] parts = row.asText("").split(",");
                    if (parts.length < 5) continue;
                    BigDecimal h = decimal(parts[3]);
                    BigDecimal l = decimal(parts[4]);
                    if (h != null && (high == null || h.compareTo(high) > 0)) high = h;
                    if (l != null && (low == null || l.compareTo(low) < 0)) low = l;
                }
            }
            return high == null || low == null ? Optional.empty() : Optional.of(new PriceRange(high, low, "EASTMONEY_DAILY_KLINE"));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String fmt(LocalDate date) { return date.toString().replace("-", ""); }
    private BigDecimal decimal(String value) { try { return new BigDecimal(value); } catch (Exception ignored) { return null; } }
    private String secid(String stockCode) {
        String code = stockCode == null ? "" : stockCode.trim().toUpperCase();
        if (code.startsWith("SH")) return "1." + code.substring(2);
        if (code.startsWith("SZ")) return "0." + code.substring(2);
        if (code.matches("6\\d{5}")) return "1." + code;
        if (code.matches("[02348]\\d{5}")) return "0." + code;
        return null;
    }
}
