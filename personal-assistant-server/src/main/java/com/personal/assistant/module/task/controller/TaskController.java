package com.personal.assistant.module.task.controller;

import com.personal.assistant.common.response.ApiResponse;
import com.personal.assistant.common.security.SecurityContextHelper;
import com.personal.assistant.module.task.dto.*;
import com.personal.assistant.module.task.entity.WorkReview;
import com.personal.assistant.module.task.service.TaskService;
import io.swagger.v3.oas.annotations.*; import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime; import java.util.List;

@Tag(name="事项管理",description="统一生活、工作和学习事项") @RestController @RequestMapping("/api/tasks")
public class TaskController {
 private final TaskService service; public TaskController(TaskService service){this.service=service;}
 @Operation(summary="筛选事项") @GetMapping public ApiResponse<List<TaskResponse>> list(@RequestParam(required=false) TaskType type,@RequestParam(required=false) String keyword,@RequestParam(required=false) TaskStatus status,@RequestParam(required=false) TaskPriority priority,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime end){return ApiResponse.success(service.list(SecurityContextHelper.currentUserId(),type,keyword,status,priority,start,end));}
 @GetMapping("/{id}") public ApiResponse<TaskResponse> get(@PathVariable Long id){return ApiResponse.success(service.get(SecurityContextHelper.currentUserId(),id));}
 @PostMapping public ApiResponse<Long> create(@Valid @RequestBody TaskUpsertRequest request){return ApiResponse.success(service.create(SecurityContextHelper.currentUserId(),request));}
 @PutMapping("/{id}") public ApiResponse<Void> update(@PathVariable Long id,@Valid @RequestBody TaskUpsertRequest request){service.update(SecurityContextHelper.currentUserId(),id,request);return ApiResponse.success();}
 @PatchMapping("/{id}/status") public ApiResponse<Void> status(@PathVariable Long id,@Valid @RequestBody TaskStatusRequest request){service.changeStatus(SecurityContextHelper.currentUserId(),id,request.status());return ApiResponse.success();}
 @PatchMapping("/{id}/archive") public ApiResponse<Void> archive(@PathVariable Long id){service.archive(SecurityContextHelper.currentUserId(),id);return ApiResponse.success();}
 @PostMapping("/{id}/reviews") public ApiResponse<Long> addReview(@PathVariable Long id,@Valid @RequestBody WorkReviewRequest request){return ApiResponse.success(service.addReview(SecurityContextHelper.currentUserId(),id,request));}
 @GetMapping("/{id}/reviews") public ApiResponse<List<WorkReview>> reviews(@PathVariable Long id){return ApiResponse.success(service.listReviews(SecurityContextHelper.currentUserId(),id));}
}