package com.personal.assistant.module.auth.controller;

import com.personal.assistant.common.response.ApiResponse;
import com.personal.assistant.common.security.AuthUser;
import com.personal.assistant.common.security.SecurityContextHelper;
import com.personal.assistant.module.auth.dto.LoginRequest;
import com.personal.assistant.module.auth.dto.LoginResponse;
import com.personal.assistant.module.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证管理", description = "登录、退出与当前用户")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @Operation(summary = "当前登录用户")
    @GetMapping("/me")
    public ApiResponse<AuthUser> me() {
        return ApiResponse.success(SecurityContextHelper.currentUser());
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        // 无状态 JWT 由前端清除令牌即可，此处保留接口用于审计与后续黑名单扩展
        return ApiResponse.success();
    }
}
