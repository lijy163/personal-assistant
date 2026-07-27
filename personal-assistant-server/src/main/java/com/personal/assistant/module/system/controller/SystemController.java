package com.personal.assistant.module.system.controller;

import com.personal.assistant.common.response.ApiResponse;
import com.personal.assistant.module.system.dto.HealthInfo;
import com.personal.assistant.module.system.dto.SystemStatus;
import com.personal.assistant.module.system.dto.StorageInfo;
import com.personal.assistant.module.system.service.SystemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "系统管理", description = "健康检查与系统信息")
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SystemService systemService;

    public SystemController(SystemService systemService) {
        this.systemService = systemService;
    }

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public ApiResponse<HealthInfo> health() {
        return ApiResponse.success(systemService.checkHealth());
    }

    @Operation(summary = "系统运行状态")
    @GetMapping("/status")
    public ApiResponse<SystemStatus> status() {
        return ApiResponse.success(systemService.status());
    }

    @Operation(summary = "存储空间")
    @GetMapping("/storage")
    public ApiResponse<StorageInfo> storage() {
        return ApiResponse.success(systemService.storage());
    }
    @Operation(summary = "系统版本信息")
    @GetMapping("/version")
    public ApiResponse<String> version() {
        return ApiResponse.success(systemService.version());
    }
}
