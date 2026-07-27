package com.personal.assistant.module.stock.controller;

import com.personal.assistant.common.response.ApiResponse;
import com.personal.assistant.common.security.SecurityContextHelper;
import com.personal.assistant.module.stock.dto.StockFundFlowOverviewResponse;
import com.personal.assistant.module.stock.dto.StockFundFlowRefreshResponse;
import com.personal.assistant.module.stock.dto.StockFundFlowStatusResponse;
import com.personal.assistant.module.stock.entity.StockFundFlowSnapshot;
import com.personal.assistant.module.stock.service.StockFundFlowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
public class StockFundFlowController {
    private final StockFundFlowService service;

    public StockFundFlowController(StockFundFlowService service) { this.service = service; }
    private Long userId() { return SecurityContextHelper.currentUserId(); }

    @PostMapping("/fund-flow/refresh")
    public ApiResponse<StockFundFlowRefreshResponse> refresh() { return ApiResponse.success(service.refresh(userId())); }

    @GetMapping("/fund-flow/overview")
    public ApiResponse<StockFundFlowOverviewResponse> overview() { return ApiResponse.success(service.overview(userId())); }

    @GetMapping("/fund-flow/status")
    public ApiResponse<StockFundFlowStatusResponse> status() { return ApiResponse.success(service.status(userId())); }

    @GetMapping("/{watchId}/fund-flow/latest")
    public ApiResponse<StockFundFlowSnapshot> latest(@PathVariable Long watchId) {
        return ApiResponse.success(service.latest(userId(), watchId));
    }

    @GetMapping("/{watchId}/fund-flow/trend")
    public ApiResponse<List<StockFundFlowSnapshot>> trend(@PathVariable Long watchId,
                                                          @RequestParam(defaultValue = "20") int days) {
        return ApiResponse.success(service.trend(userId(), watchId, days));
    }
}
