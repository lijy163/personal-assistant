package com.personal.assistant.module.stock.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.stock.entity.StockWatchItem;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class EastMoneyStockFundFlowProvider implements StockFundFlowProvider {
    private static final String URL = "https://push2his.eastmoney.com/api/qt/stock/fflow/daykline/get?secid=%s&lmt=%d&klt=101&fields1=f1,f2,f3,f7&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61,f62,f63";
    private final RestClient client = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override public String name() { return "EASTMONEY"; }

    @Override
    public List<StockFundFlowPoint> fetchDaily(StockWatchItem item, int limit) {
        if (!"CN".equals(item.getMarket())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "资金流首期仅支持 A 股");
        }
        try {
            String raw = client.get().uri(URL.formatted(secid(item.getStockCode()), limit)).retrieve().body(String.class);
            JsonNode data = objectMapper.readTree(raw).path("data");
            if (data.isMissingNode() || data.isNull() || !data.path("klines").isArray()) {
                throw new IllegalStateException("供应商未返回资金流数据");
            }
            List<StockFundFlowPoint> points = new ArrayList<>();
            for (JsonNode line : data.path("klines")) {
                String[] fields = line.asText().split(",", -1);
                if (fields.length < 13) continue;
                points.add(new StockFundFlowPoint(
                        decimal(fields[1]), decimal(fields[6]), decimal(fields[5]), decimal(fields[10]),
                        decimal(fields[4]), decimal(fields[9]), decimal(fields[3]), decimal(fields[8]),
                        decimal(fields[2]), decimal(fields[7]), decimal(fields[11]), decimal(fields[12]), null,
                        LocalDateTime.of(LocalDate.parse(fields[0]), LocalTime.of(15, 0))));
            }
            if (points.isEmpty()) throw new IllegalStateException("供应商资金流数据为空");
            return points;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTEGRATION_ERROR, "东方财富资金流获取失败：" + exception.getMessage());
        }
    }

    private String secid(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase();
        if (normalized.startsWith("SH")) return "1." + normalized.substring(2);
        if (normalized.startsWith("SZ") || normalized.startsWith("BJ")) return "0." + normalized.substring(2);
        if (normalized.matches("^(5|6|9).*")) return "1." + normalized;
        if (normalized.matches("^(0|1|2|3|4|8).*")) return "0." + normalized;
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "无法识别 A 股代码：" + code);
    }

    private BigDecimal decimal(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) return null;
        return new BigDecimal(value);
    }
}
