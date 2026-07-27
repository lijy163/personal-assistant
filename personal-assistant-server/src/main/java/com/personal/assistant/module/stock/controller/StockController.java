package com.personal.assistant.module.stock.controller;

import com.personal.assistant.common.response.ApiResponse;
import com.personal.assistant.common.security.SecurityContextHelper;
import com.personal.assistant.module.stock.dto.StockApiConfigRequest;
import com.personal.assistant.module.stock.dto.StockApiConfigResponse;
import com.personal.assistant.module.stock.dto.StockMarketMapResponse;
import com.personal.assistant.module.stock.dto.StockQuoteRefreshResponse;
import com.personal.assistant.module.stock.dto.StockQuoteStatusResponse;
import com.personal.assistant.module.stock.dto.StockWatchRequest;
import com.personal.assistant.module.stock.entity.StockCollectionResult;
import com.personal.assistant.module.stock.entity.StockWatchItem;
import com.personal.assistant.module.stock.service.StockService;
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
@RequestMapping("/api/stocks")
public class StockController {
    private final StockService service;

    public StockController(StockService service) {
        this.service = service;
    }

    private Long uid() {
        return SecurityContextHelper.currentUserId();
    }

    @GetMapping("/watch-items")
    public ApiResponse<List<StockWatchItem>> watches(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String market,
                                                     @RequestParam(required = false) String tag,
                                                     @RequestParam(required = false) Boolean enabled) {
        return ApiResponse.success(service.listWatches(uid(), keyword, market, tag, enabled));
    }

    @GetMapping("/market-map")
    public ApiResponse<StockMarketMapResponse> marketMap(@RequestParam(required = false) String market,
                                                         @RequestParam(defaultValue = "true") boolean enabledOnly) {
        return ApiResponse.success(service.marketMap(uid(), market, enabledOnly));
    }

    @PostMapping("/quotes/refresh")
    public ApiResponse<StockQuoteRefreshResponse> refreshQuotes(@RequestParam(required = false) String market,
                                                                @RequestParam(defaultValue = "true") boolean enabledOnly) {
        return ApiResponse.success(service.refreshQuotes(uid(), market, enabledOnly));
    }

    @GetMapping("/quotes/status")
    public ApiResponse<StockQuoteStatusResponse> quoteStatus(@RequestParam(required = false) String market,
                                                             @RequestParam(defaultValue = "true") boolean enabledOnly) {
        return ApiResponse.success(service.quoteStatus(uid(), market, enabledOnly));
    }

    @PostMapping("/watch-items")
    public ApiResponse<Long> save(@Valid @RequestBody StockWatchRequest request) {
        return ApiResponse.success(service.saveWatch(uid(), null, request));
    }

    @PutMapping("/watch-items/{id}")
    public ApiResponse<Long> save(@PathVariable Long id, @Valid @RequestBody StockWatchRequest request) {
        return ApiResponse.success(service.saveWatch(uid(), id, request));
    }

    @PatchMapping("/watch-items/{id}/enabled")
    public ApiResponse<Void> toggle(@PathVariable Long id, @RequestParam boolean enabled) {
        service.toggleWatch(uid(), id, enabled);
        return ApiResponse.success();
    }

    @GetMapping("/api-configs")
    public ApiResponse<List<StockApiConfigResponse>> configs() {
        return ApiResponse.success(service.listConfigs(uid()));
    }

    @PostMapping("/api-configs")
    public ApiResponse<Long> config(@Valid @RequestBody StockApiConfigRequest request) {
        return ApiResponse.success(service.saveConfig(uid(), null, request));
    }

    @PutMapping("/api-configs/{id}")
    public ApiResponse<Long> config(@PathVariable Long id, @Valid @RequestBody StockApiConfigRequest request) {
        return ApiResponse.success(service.saveConfig(uid(), id, request));
    }

    @PostMapping("/api-configs/{id}/test")
    public ApiResponse<Boolean> test(@PathVariable Long id) {
        return ApiResponse.success(service.testConfig(uid(), id));
    }

    @GetMapping("/collection-results")
    public ApiResponse<List<StockCollectionResult>> results(@RequestParam(required = false) Long watchId) {
        return ApiResponse.success(service.listResults(uid(), watchId));
    }

    @PostMapping("/collect")
    public ApiResponse<Integer> collect() {
        return ApiResponse.success(service.collectEnabled());
    }
}