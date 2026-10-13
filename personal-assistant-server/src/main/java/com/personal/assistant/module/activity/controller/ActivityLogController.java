package com.personal.assistant.module.activity.controller;

import com.personal.assistant.common.response.ApiResponse;
import com.personal.assistant.common.security.SecurityContextHelper;
import com.personal.assistant.module.activity.dto.ActivityDaySummary;
import com.personal.assistant.module.activity.dto.ActivityLogCreateRequest;
import com.personal.assistant.module.activity.dto.ActivityLogResponse;
import com.personal.assistant.module.activity.dto.ActivityLogUpdateRequest;
import com.personal.assistant.module.activity.dto.ActivityTagSummary;
import com.personal.assistant.module.activity.service.ActivityLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "每日活动记录", description = "随时如实记录每天做了什么事情")
@RestController
@RequestMapping("/api/activity-logs")
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    public ActivityLogController(ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    @Operation(summary = "新增活动记录")
    @PostMapping
    public ApiResponse<ActivityLogResponse> create(@Valid @RequestBody ActivityLogCreateRequest request) {
        return ApiResponse.success(activityLogService.create(SecurityContextHelper.currentUserId(), request));
    }

    @Operation(summary = "查询活动记录")
    @GetMapping
    public ApiResponse<List<ActivityLogResponse>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(activityLogService.list(
                SecurityContextHelper.currentUserId(), from, to, keyword, category, tag, limit));
    }

    @Operation(summary = "查询某天时间线")
    @GetMapping("/timeline")
    public ApiResponse<List<ActivityLogResponse>> timeline(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(activityLogService.timeline(SecurityContextHelper.currentUserId(), date));
    }

    @Operation(summary = "查询日历聚合")
    @GetMapping("/calendar")
    public ApiResponse<List<ActivityDaySummary>> calendar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(activityLogService.calendar(SecurityContextHelper.currentUserId(), from, to));
    }

    @Operation(summary = "查询标签聚合")
    @GetMapping("/tags")
    public ApiResponse<List<ActivityTagSummary>> tags(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(activityLogService.tags(SecurityContextHelper.currentUserId(), from, to));
    }

    @Operation(summary = "更新活动记录")
    @PutMapping("/{id}")
    public ApiResponse<ActivityLogResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody ActivityLogUpdateRequest request) {
        return ApiResponse.success(activityLogService.update(SecurityContextHelper.currentUserId(), id, request));
    }

    @Operation(summary = "置顶或取消置顶")
    @PatchMapping("/{id}/pin")
    public ApiResponse<ActivityLogResponse> togglePin(@PathVariable Long id) {
        return ApiResponse.success(activityLogService.togglePin(SecurityContextHelper.currentUserId(), id));
    }

    @Operation(summary = "删除活动记录")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        activityLogService.delete(SecurityContextHelper.currentUserId(), id);
        return ApiResponse.success();
    }
}
