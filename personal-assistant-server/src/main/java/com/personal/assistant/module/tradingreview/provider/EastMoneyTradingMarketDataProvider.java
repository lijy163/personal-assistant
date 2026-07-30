package com.personal.assistant.module.tradingreview.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.tradingreview.dto.MarketSnapshot;
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
    private static final String MARKET_URL = "https://push2.eastmoney.com/api/qt/clist/get?pn=1&pz=6000&po=1&np=1&fltt=2&invt=2&fid=f3&fs=m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23&fields=f3,f6";
    private static final String INDEX_URL = "https://push2.eastmoney.com/api/qt/ulist.np/get?fltt=2&secids=1.000001,0.399001,0.399006&fields=f3,f6";
    private static final String SECTOR_URL = "https://push2.eastmoney.com/api/qt/clist/get?pn=1&pz=8&po=1&np=1&fltt=2&invt=2&fid=f3&fs=%s&fields=f12,f14,f3";
    private final RestClient client = RestClient.builder().defaultHeader("User-Agent", "Mozilla/5.0 personal-assistant").build();
    private final ObjectMapper objectMapper;

    public EastMoneyTradingMarketDataProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "EASTMONEY";
    }

    @Override
    public MarketSnapshot fetch(LocalDate tradeDate) {
        try {
            JsonNode market = get(MARKET_URL).path("data").path("diff");
            if (!market.isArray() || market.isEmpty()) {
                throw new IllegalStateException("东方财富未返回全市场行情");
            }
            int rising = 0;
            int falling = 0;
            int flat = 0;
            int limitUp = 0;
            int limitDown = 0;
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
            String industries = sectors("m:90+t:2");
            String concepts = sectors("m:90+t:3");
            String raw = objectMapper.createObjectNode()
                    .put("tradeDate", tradeDate.toString())
                    .put("marketCount", market.size())
                    .put("limitMethod", "涨跌幅阈值近似，待 Tushare 校准")
                    .put("brokenBoard", "东方财富公开接口首期未稳定获取")
                    .put("streak", "东方财富公开接口首期未稳定获取")
                    .toString();
            return new MarketSnapshot(shanghai, shenzhen, chinext, rising, falling, flat, limitUp, limitDown,
                    null, null, null, turnover, null, industries, concepts, name(), LocalDateTime.now(SHANGHAI), raw);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "东方财富行情采集失败：" + exception.getMessage());
        }
    }

    private JsonNode get(String url) {
        String body = client.get().uri(url).retrieve().body(String.class);
        try {
            return objectMapper.readTree(body);
        } catch (Exception exception) {
            throw new IllegalStateException("行情响应解析失败", exception);
        }
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
        try {
            return value.decimalValue();
        } catch (Exception ignored) {
            return null;
        }
    }
}
