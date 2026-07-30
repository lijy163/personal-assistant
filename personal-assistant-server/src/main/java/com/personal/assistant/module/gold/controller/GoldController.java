package com.personal.assistant.module.gold.controller;

import com.personal.assistant.common.response.ApiResponse;
import com.personal.assistant.common.security.SecurityContextHelper;
import com.personal.assistant.module.gold.dto.GoldApiConfigRequest;
import com.personal.assistant.module.gold.dto.GoldApiConfigResponse;
import com.personal.assistant.module.gold.dto.GoldPriceAlertRuleResponse;
import com.personal.assistant.module.gold.dto.GoldQuoteRefreshResponse;
import com.personal.assistant.module.gold.dto.GoldPublicQuoteResponse;
import com.personal.assistant.module.gold.dto.GoldQuoteStatusResponse;
import com.personal.assistant.module.gold.dto.GoldWatchRequest;
import com.personal.assistant.module.gold.entity.GoldCollectionResult;
import com.personal.assistant.module.gold.entity.GoldWatchItem;
import com.personal.assistant.module.gold.service.GoldPriceAlertService;
import com.personal.assistant.module.gold.service.GoldService;
import com.personal.assistant.module.gold.service.PublicGoldQuoteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/gold")
public class GoldController {
    private final GoldService service;
    private final PublicGoldQuoteService publicQuoteService;
    private final GoldPriceAlertService alertService;

    public GoldController(GoldService service, PublicGoldQuoteService publicQuoteService,
                          GoldPriceAlertService alertService) {
        this.service = service;
        this.publicQuoteService = publicQuoteService;
        this.alertService = alertService;
    }

    private Long uid() {
        return SecurityContextHelper.currentUserId();
    }

    @GetMapping("/watch-items")
    public ApiResponse<List<GoldWatchItem>> watches(@RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String goldType,
                                                    @RequestParam(required = false) Boolean enabled) {
        return ApiResponse.success(service.listWatches(uid(), keyword, goldType, enabled));
    }

    @PostMapping("/watch-items")
    public ApiResponse<Long> save(@Valid @RequestBody GoldWatchRequest request) {
        return ApiResponse.success(service.saveWatch(uid(), null, request));
    }

    @PutMapping("/watch-items/{id}")
    public ApiResponse<Long> save(@PathVariable Long id, @Valid @RequestBody GoldWatchRequest request) {
        return ApiResponse.success(service.saveWatch(uid(), id, request));
    }

    @PatchMapping("/watch-items/{id}/enabled")
    public ApiResponse<Void> toggle(@PathVariable Long id, @RequestParam boolean enabled) {
        service.toggleWatch(uid(), id, enabled);
        return ApiResponse.success();
    }

    @GetMapping("/api-configs")
    public ApiResponse<List<GoldApiConfigResponse>> configs() {
        return ApiResponse.success(service.listConfigs(uid()));
    }

    @PostMapping("/api-configs")
    public ApiResponse<Long> config(@Valid @RequestBody GoldApiConfigRequest request) {
        return ApiResponse.success(service.saveConfig(uid(), null, request));
    }

    @PutMapping("/api-configs/{id}")
    public ApiResponse<Long> config(@PathVariable Long id, @Valid @RequestBody GoldApiConfigRequest request) {
        return ApiResponse.success(service.saveConfig(uid(), id, request));
    }

    @PostMapping("/api-configs/{id}/test")
    public ApiResponse<Boolean> test(@PathVariable Long id) {
        return ApiResponse.success(service.testConfig(uid(), id));
    }

    @GetMapping("/collection-results")
    public ApiResponse<List<GoldCollectionResult>> results(@RequestParam(required = false) Long watchId) {
        return ApiResponse.success(service.listResults(uid(), watchId));
    }

    @GetMapping("/public-quotes")
    public ApiResponse<GoldPublicQuoteResponse> publicQuotes() {
        return ApiResponse.success(publicQuoteService.latest());
    }

    @GetMapping("/alert-rules")
    public ApiResponse<List<GoldPriceAlertRuleResponse>> alertRules() {
        return ApiResponse.success(alertService.listRules(uid()));
    }
    @PostMapping("/quotes/refresh")
    public ApiResponse<GoldQuoteRefreshResponse> refreshQuotes(@RequestParam(required = false) String goldType,
                                                               @RequestParam(defaultValue = "true") boolean enabledOnly) {
        return ApiResponse.success(service.refreshQuotes(uid(), goldType, enabledOnly));
    }

    @GetMapping("/quotes/status")
    public ApiResponse<GoldQuoteStatusResponse> quoteStatus(@RequestParam(required = false) String goldType,
                                                            @RequestParam(defaultValue = "true") boolean enabledOnly) {
        return ApiResponse.success(service.quoteStatus(uid(), goldType, enabledOnly));
    }

    @PostMapping("/collect")
    public ApiResponse<Integer> collect() {
        return ApiResponse.success(service.collectEnabled());
    }
}
