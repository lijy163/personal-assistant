package com.personal.assistant.module.codexagent.controller;

import com.personal.assistant.common.response.ApiResponse;
import com.personal.assistant.common.security.SecurityContextHelper;
import com.personal.assistant.module.codexagent.service.CodexCloudConfigService;
import com.personal.assistant.module.codexagent.service.CodexCloudConfigService.ConfigView;
import com.personal.assistant.module.codexagent.service.CodexCloudConfigService.SaveRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/codex-agents/cloud-config")
public class CodexCloudConfigController {
    private final CodexCloudConfigService service;

    public CodexCloudConfigController(CodexCloudConfigService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<ConfigView> get() {
        return ApiResponse.success(service.get(SecurityContextHelper.currentUserId()));
    }

    @PutMapping
    public ApiResponse<ConfigView> save(@Valid @RequestBody SaveRequest request) {
        return ApiResponse.success(service.save(SecurityContextHelper.currentUserId(), request));
    }
}
