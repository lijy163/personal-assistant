package com.personal.assistant.module.finance.controller;

import com.personal.assistant.common.response.ApiResponse;
import com.personal.assistant.common.security.SecurityContextHelper;
import com.personal.assistant.module.finance.dto.FinanceMonthlyAnalysis;
import com.personal.assistant.module.finance.dto.FinanceTransactionRequest;
import com.personal.assistant.module.finance.service.FinanceManualService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/finance")
public class FinanceManualController {
    private final FinanceManualService service;

    public FinanceManualController(FinanceManualService service) {
        this.service = service;
    }

    @PostMapping("/manual-transactions")
    public ApiResponse<Long> create(@Valid @RequestBody FinanceTransactionRequest request) {
        return ApiResponse.success(service.save(userId(), null, request));
    }

    @PutMapping("/manual-transactions/{id}")
    public ApiResponse<Long> update(@PathVariable Long id, @Valid @RequestBody FinanceTransactionRequest request) {
        return ApiResponse.success(service.save(userId(), id, request));
    }

    @DeleteMapping("/manual-transactions/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(userId(), id);
        return ApiResponse.success();
    }

    @GetMapping("/stats/monthly-analysis")
    public ApiResponse<FinanceMonthlyAnalysis> monthly(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return ApiResponse.success(service.monthly(userId(), month));
    }

    private Long userId() {
        return SecurityContextHelper.currentUserId();
    }
}
