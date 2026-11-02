package com.personal.assistant.module.checkin.controller;

import com.personal.assistant.common.response.ApiResponse;
import com.personal.assistant.common.security.SecurityContextHelper;
import com.personal.assistant.module.checkin.dto.CheckinDaySummary;
import com.personal.assistant.module.checkin.dto.CheckinOverview;
import com.personal.assistant.module.checkin.dto.CheckinResult;
import com.personal.assistant.module.checkin.dto.HabitRemovalResult;
import com.personal.assistant.module.checkin.dto.HabitRequest;
import com.personal.assistant.module.checkin.service.CheckinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.time.LocalDate;
import java.util.List;

@Tag(name = "每日打卡", description = "按打卡项完成当天的固定动作，并同步写入每日活动记录")
@RestController
@RequestMapping("/api/checkins")
public class CheckinController {

    private final CheckinService checkinService;

    public CheckinController(CheckinService checkinService) {
        this.checkinService = checkinService;
    }

    @Operation(summary = "查询某天的打卡概览")
    @GetMapping("/overview")
    public ApiResponse<CheckinOverview> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(checkinService.overview(uid(), date));
    }

    @Operation(summary = "查询打卡日历聚合")
    @GetMapping("/calendar")
    public ApiResponse<List<CheckinDaySummary>> calendar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(checkinService.calendar(uid(), from, to));
    }

    @Operation(summary = "新增打卡项")
    @PostMapping("/habits")
    public ApiResponse<Long> createHabit(@Valid @RequestBody HabitRequest request) {
        return ApiResponse.success(checkinService.createHabit(uid(), request));
    }

    @Operation(summary = "修改打卡项")
    @PutMapping("/habits/{id}")
    public ApiResponse<Void> updateHabit(@PathVariable Long id, @Valid @RequestBody HabitRequest request) {
        checkinService.updateHabit(uid(), id, request);
        return ApiResponse.success();
    }

    @Operation(summary = "删除或停用打卡项")
    @DeleteMapping("/habits/{id}")
    public ApiResponse<HabitRemovalResult> removeHabit(@PathVariable Long id) {
        return ApiResponse.success(checkinService.removeHabit(uid(), id));
    }

    @Operation(summary = "打卡")
    @PostMapping("/habits/{id}/check")
    public ApiResponse<CheckinResult> check(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String note) {
        return ApiResponse.success(checkinService.check(uid(), id, date, note));
    }

    @Operation(summary = "撤销打卡")
    @DeleteMapping("/habits/{id}/check")
    public ApiResponse<CheckinResult> cancel(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(checkinService.cancel(uid(), id, date));
    }

    private Long uid() {
        return SecurityContextHelper.currentUserId();
    }
}
