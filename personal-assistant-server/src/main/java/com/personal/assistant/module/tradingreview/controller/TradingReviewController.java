package com.personal.assistant.module.tradingreview.controller;

import com.personal.assistant.common.response.ApiResponse;
import com.personal.assistant.common.security.SecurityContextHelper;
import com.personal.assistant.module.tradingreview.dto.*;
import com.personal.assistant.module.tradingreview.entity.*;
import com.personal.assistant.module.tradingreview.service.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/trading-reviews")
public class TradingReviewController {
    private final TradingReviewService service;
    private final TradingReviewUpsertService reviewUpsertService;
    private final TradingMarketCollectionService collectionService;
    private final TradingStatisticsService statisticsService;
    private final TradingExecutionStateService executionStateService;
    private final TradingReviewAnalyticsService analyticsService;

    public TradingReviewController(TradingReviewService service, TradingReviewUpsertService reviewUpsertService,
                                   TradingMarketCollectionService collectionService, TradingStatisticsService statisticsService,
                                   TradingExecutionStateService executionStateService, TradingReviewAnalyticsService analyticsService) {
        this.service = service; this.reviewUpsertService = reviewUpsertService;
        this.collectionService = collectionService; this.statisticsService = statisticsService;
        this.executionStateService = executionStateService; this.analyticsService = analyticsService;
    }

    private Long uid(){return SecurityContextHelper.currentUserId();}
    @GetMapping("/reviews") public ApiResponse<List<TradingDailyReview>> reviews(){return ApiResponse.success(service.reviews(uid()));}
    @GetMapping("/reviews/{id}") public ApiResponse<TradingDailyReview> review(@PathVariable Long id){return ApiResponse.success(service.review(uid(),id));}
    @PostMapping("/reviews") public ApiResponse<Long> createReview(@Valid @RequestBody ReviewRequest request){return ApiResponse.success(reviewUpsertService.save(uid(),request));}
    @PutMapping("/reviews/{id}") public ApiResponse<Long> updateReview(@PathVariable Long id,@Valid @RequestBody ReviewRequest request){return ApiResponse.success(service.saveReview(uid(),id,request));}
    @DeleteMapping("/reviews/{id}") public ApiResponse<Void> deleteReview(@PathVariable Long id){service.deleteReview(uid(),id);return ApiResponse.success();}
    @PostMapping("/market/refresh") public ApiResponse<CollectionResponse> refresh(@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate tradeDate,@RequestParam(required=false)String snapshotType){return ApiResponse.success(collectionService.refresh(uid(),tradeDate,snapshotType));}
    @GetMapping("/analytics") public ApiResponse<TradingReviewAnalyticsResponse> analytics(@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate tradeDate){return ApiResponse.success(analyticsService.analytics(uid(),tradeDate));}
    @GetMapping("/trades") public ApiResponse<List<TradingLog>> trades(){return ApiResponse.success(service.trades(uid()));}
    @GetMapping("/trades/{id}") public ApiResponse<TradeDetailResponse> trade(@PathVariable Long id){return ApiResponse.success(service.trade(uid(),id));}
    @PostMapping("/trades") public ApiResponse<Long> createTrade(@Valid @RequestBody TradeRequest request){return ApiResponse.success(service.saveTrade(uid(),null,request));}
    @PutMapping("/trades/{id}") public ApiResponse<Long> updateTrade(@PathVariable Long id,@Valid @RequestBody TradeRequest request){return ApiResponse.success(service.saveTrade(uid(),id,request));}
    @DeleteMapping("/trades/{id}") public ApiResponse<Void> deleteTrade(@PathVariable Long id){service.deleteTrade(uid(),id);return ApiResponse.success();}
    @PostMapping("/trades/{tradeId}/executions") public ApiResponse<Long> addExecution(@PathVariable Long tradeId,@Valid @RequestBody ExecutionRequest request){return ApiResponse.success(service.addExecution(uid(),tradeId,request));}
    @DeleteMapping("/trades/{tradeId}/executions/{id}") public ApiResponse<Void> deleteExecution(@PathVariable Long tradeId,@PathVariable Long id){executionStateService.delete(uid(),tradeId,id);return ApiResponse.success();}
    @GetMapping("/plans") public ApiResponse<List<TradingNextPlan>> plans(){return ApiResponse.success(service.plans(uid()));}
    @PostMapping("/plans") public ApiResponse<Long> createPlan(@Valid @RequestBody PlanRequest request){return ApiResponse.success(service.savePlan(uid(),null,request));}
    @PutMapping("/plans/{id}") public ApiResponse<Long> updatePlan(@PathVariable Long id,@Valid @RequestBody PlanRequest request){return ApiResponse.success(service.savePlan(uid(),id,request));}
    @DeleteMapping("/plans/{id}") public ApiResponse<Void> deletePlan(@PathVariable Long id){service.deletePlan(uid(),id);return ApiResponse.success();}
    @GetMapping("/mistakes") public ApiResponse<List<TradingMistake>> mistakes(){return ApiResponse.success(service.mistakes(uid()));}
    @PostMapping("/mistakes") public ApiResponse<Long> createMistake(@Valid @RequestBody MistakeRequest request){return ApiResponse.success(service.saveMistake(uid(),null,request));}
    @PutMapping("/mistakes/{id}") public ApiResponse<Long> updateMistake(@PathVariable Long id,@Valid @RequestBody MistakeRequest request){return ApiResponse.success(service.saveMistake(uid(),id,request));}
    @DeleteMapping("/mistakes/{id}") public ApiResponse<Void> deleteMistake(@PathVariable Long id){service.deleteMistake(uid(),id);return ApiResponse.success();}
    @GetMapping("/stats") public ApiResponse<TradingStatsResponse> stats(){return ApiResponse.success(statisticsService.calculate(uid()));}
}
