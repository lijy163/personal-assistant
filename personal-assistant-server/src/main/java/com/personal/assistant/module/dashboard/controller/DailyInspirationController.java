package com.personal.assistant.module.dashboard.controller;

import com.personal.assistant.common.response.ApiResponse;
import com.personal.assistant.module.dashboard.dto.DailyInspirationResponse;
import com.personal.assistant.module.dashboard.service.DailyInspirationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DailyInspirationController {
    private final DailyInspirationService service;

    public DailyInspirationController(DailyInspirationService service) {
        this.service = service;
    }

    @GetMapping("/daily-inspiration")
    public ApiResponse<DailyInspirationResponse> today() {
        return ApiResponse.success(service.today());
    }
}
