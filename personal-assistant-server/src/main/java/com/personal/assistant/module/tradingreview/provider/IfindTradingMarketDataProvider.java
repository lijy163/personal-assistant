package com.personal.assistant.module.tradingreview.provider;

import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.tradingreview.dto.MarketSnapshot;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class IfindTradingMarketDataProvider implements TradingMarketDataProvider {
    private final IfindProperties properties;

    public IfindTradingMarketDataProvider(IfindProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() { return "IFIND"; }

    public boolean available() {
        return properties.isEnabled()
                && properties.getAccessToken() != null
                && !properties.getAccessToken().isBlank();
    }

    @Override
    public MarketSnapshot fetch(LocalDate tradeDate) {
        if (!available()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "iFinD 未启用或缺少 accessToken");
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                "iFinD Provider 已接入配置框架，待配置正式指标接口后启用采集；当前将自动降级到东方财富。");
    }
}
