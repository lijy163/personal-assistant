package com.personal.assistant.module.devlog.controller;

import com.personal.assistant.common.response.ApiResponse;
import com.personal.assistant.common.security.SecurityContextHelper;
import com.personal.assistant.module.devlog.dto.PatCreateRequest;
import com.personal.assistant.module.devlog.dto.PatCreateResponse;
import com.personal.assistant.module.devlog.dto.PatSummary;
import com.personal.assistant.module.devlog.service.PersonalAccessTokenService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/personal-access-tokens")
public class PersonalAccessTokenController {
    private final PersonalAccessTokenService tokenService;

    public PersonalAccessTokenController(PersonalAccessTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping
    public ApiResponse<PatCreateResponse> create(@Valid @RequestBody PatCreateRequest request) {
        return ApiResponse.success(tokenService.create(SecurityContextHelper.currentUserId(), request));
    }

    @GetMapping
    public ApiResponse<List<PatSummary>> list() {
        return ApiResponse.success(tokenService.list(SecurityContextHelper.currentUserId()));
    }

    @PatchMapping("/{id}/revoke")
    public ApiResponse<Void> revoke(@PathVariable Long id) {
        tokenService.revoke(SecurityContextHelper.currentUserId(), id);
        return ApiResponse.success();
    }
}
