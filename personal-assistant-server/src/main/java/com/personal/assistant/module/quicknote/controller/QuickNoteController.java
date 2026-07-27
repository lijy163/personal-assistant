package com.personal.assistant.module.quicknote.controller;

import com.personal.assistant.common.response.ApiResponse;
import com.personal.assistant.common.security.SecurityContextHelper;
import com.personal.assistant.module.quicknote.dto.QuickNoteCreateRequest;
import com.personal.assistant.module.quicknote.entity.QuickNote;
import com.personal.assistant.module.quicknote.service.QuickNoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "快速记录", description = "随手记入口，后续归类整理")
@RestController
@RequestMapping("/api/quick-notes")
public class QuickNoteController {

    private final QuickNoteService quickNoteService;

    public QuickNoteController(QuickNoteService quickNoteService) {
        this.quickNoteService = quickNoteService;
    }

    @Operation(summary = "新建快速记录")
    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody QuickNoteCreateRequest request) {
        Long id = quickNoteService.create(SecurityContextHelper.currentUserId(), request);
        return ApiResponse.success(id);
    }

    @Operation(summary = "查询待整理的快速记录")
    @GetMapping("/pending")
    public ApiResponse<List<QuickNote>> listPending() {
        return ApiResponse.success(quickNoteService.listPending(SecurityContextHelper.currentUserId()));
    }

    @Operation(summary = "归档快速记录")
    @PatchMapping("/{id}/archive")
    public ApiResponse<Void> archive(@PathVariable Long id) {
        quickNoteService.archive(SecurityContextHelper.currentUserId(), id);
        return ApiResponse.success();
    }
}
