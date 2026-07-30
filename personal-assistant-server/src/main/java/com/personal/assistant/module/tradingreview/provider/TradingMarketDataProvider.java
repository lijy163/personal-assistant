package com.personal.assistant.module.tradingreview.provider;

import com.personal.assistant.module.tradingreview.dto.MarketSnapshot;
import java.time.LocalDate;

public interface TradingMarketDataProvider {
    String name();

    MarketSnapshot fetch(LocalDate tradeDate);
}
