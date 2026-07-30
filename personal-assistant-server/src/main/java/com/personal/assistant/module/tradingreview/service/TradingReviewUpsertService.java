package com.personal.assistant.module.tradingreview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.module.tradingreview.dto.ReviewRequest;
import com.personal.assistant.module.tradingreview.entity.TradingDailyReview;
import com.personal.assistant.module.tradingreview.mapper.TradingDailyReviewMapper;
import org.springframework.stereotype.Service;

@Service
public class TradingReviewUpsertService {
    private final TradingDailyReviewMapper mapper;
    private final TradingReviewService service;

    public TradingReviewUpsertService(TradingDailyReviewMapper mapper, TradingReviewService service) {
        this.mapper = mapper;
        this.service = service;
    }

    public Long save(Long userId, ReviewRequest request) {
        TradingDailyReview existing = mapper.selectOne(new LambdaQueryWrapper<TradingDailyReview>()
                .eq(TradingDailyReview::getUserId, userId)
                .eq(TradingDailyReview::getTradeDate, request.tradeDate())
                .eq(TradingDailyReview::getSnapshotType, request.snapshotType().toUpperCase()));
        return service.saveReview(userId, existing == null ? null : existing.getId(), request);
    }
}
