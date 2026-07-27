package com.personal.assistant.module.devlog.controller;

import com.personal.assistant.common.response.ApiResponse;
import com.personal.assistant.common.security.SecurityContextHelper;
import com.personal.assistant.module.devlog.dto.DevLogIngestRequest;
import com.personal.assistant.module.devlog.dto.DevLogSummary;
import com.personal.assistant.module.devlog.entity.DevLog;
import com.personal.assistant.module.devlog.service.DevLogService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/devlogs")
public class DevLogController {
    private final DevLogService devLogService;

    public DevLogController(DevLogService devLogService) {
        this.devLogService = devLogService;
    }

    @PostMapping("/ingest")
    @PreAuthorize("hasAuthority('devlog:write')")
    public ApiResponse<Long> ingest(@Valid @RequestBody DevLogIngestRequest request) {
        return ApiResponse.success(devLogService.ingest(SecurityContextHelper.currentUserId(), request));
    }

    @GetMapping
    public ApiResponse<List<DevLogSummary>> list(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String projectName) {
        return ApiResponse.success(devLogService.list(SecurityContextHelper.currentUserId(), keyword, projectName));
    }

    @GetMapping("/{id}")
    public ApiResponse<DevLog> detail(@PathVariable Long id) {
        return ApiResponse.success(devLogService.detail(SecurityContextHelper.currentUserId(), id));
    }
}
