package com.personal.assistant.module.tradingreview.service;

import com.personal.assistant.module.tradingreview.dto.TradeDetailResponse;
import com.personal.assistant.module.tradingreview.entity.TradingLog;
import com.personal.assistant.module.tradingreview.mapper.TradingLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class TradingExecutionStateService {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private final TradingReviewService reviewService;
    private final TradingLogMapper tradeMapper;

    public TradingExecutionStateService(TradingReviewService reviewService, TradingLogMapper tradeMapper) {
        this.reviewService = reviewService;
        this.tradeMapper = tradeMapper;
    }

    @Transactional
    public void delete(Long userId, Long tradeId, Long executionId) {
        reviewService.deleteExecution(userId, tradeId, executionId);
        TradeDetailResponse detail = reviewService.trade(userId, tradeId);
        TradingLog trade = detail.trade();
        boolean closed = detail.metrics().buyQuantity().signum() > 0 && detail.metrics().remainingQuantity().signum() == 0;
        trade.setStatus(closed ? "CLOSED" : "OPEN");
        if (!closed) trade.setClosedAt(null);
        trade.setUpdatedAt(LocalDateTime.now(SHANGHAI));
        tradeMapper.updateById(trade);
    }
}
