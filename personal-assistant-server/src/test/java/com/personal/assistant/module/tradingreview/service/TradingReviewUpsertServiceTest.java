package com.personal.assistant.module.tradingreview.service;

import com.personal.assistant.module.tradingreview.dto.ReviewRequest;
import com.personal.assistant.module.tradingreview.entity.TradingDailyReview;
import com.personal.assistant.module.tradingreview.mapper.TradingDailyReviewMapper;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TradingReviewUpsertServiceTest {
    @Test
    void reusesExistingSnapshotId() {
        TradingDailyReviewMapper mapper = mock(TradingDailyReviewMapper.class);
        TradingReviewService service = mock(TradingReviewService.class);
        TradingDailyReview existing = new TradingDailyReview(); existing.setId(19L);
        when(mapper.selectOne(any())).thenReturn(existing);
        ReviewRequest request = new ReviewRequest(LocalDate.of(2026, 7, 30), "FINAL", true, "DRAFT",
                null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null);
        when(service.saveReview(eq(7L), eq(19L), same(request))).thenReturn(19L);

        assertEquals(19L, new TradingReviewUpsertService(mapper, service).save(7L, request));
        verify(service).saveReview(7L, 19L, request);
    }
}
