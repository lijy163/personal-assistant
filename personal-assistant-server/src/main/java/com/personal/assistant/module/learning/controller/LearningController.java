package com.personal.assistant.module.learning.controller;
import com.personal.assistant.common.response.ApiResponse;import com.personal.assistant.common.security.SecurityContextHelper;import com.personal.assistant.module.learning.dto.*;import com.personal.assistant.module.learning.entity.*;import com.personal.assistant.module.learning.service.LearningService;import jakarta.validation.Valid;import org.springframework.web.bind.annotation.*;import java.util.List;
@RestController @RequestMapping("/api/learning") public class LearningController {
 private final LearningService service;public LearningController(LearningService s){service=s;}private Long uid(){return SecurityContextHelper.currentUserId();}
 @GetMapping("/plans") public ApiResponse<List<LearningPlan>> plans(@RequestParam(required=false)String keyword,@RequestParam(required=false)String topic,@RequestParam(required=false)String status){return ApiResponse.success(service.listPlans(uid(),keyword,topic,status));}
 @GetMapping("/plans/{id}") public ApiResponse<LearningPlan> plan(@PathVariable Long id){return ApiResponse.success(service.getPlan(uid(),id));}
 @PostMapping("/plans") public ApiResponse<Long> createPlan(@Valid @RequestBody LearningPlanRequest r){return ApiResponse.success(service.createPlan(uid(),r));}
 @PutMapping("/plans/{id}") public ApiResponse<Void> updatePlan(@PathVariable Long id,@Valid @RequestBody LearningPlanRequest r){service.updatePlan(uid(),id,r);return ApiResponse.success();}
 @PatchMapping("/plans/{id}/archive") public ApiResponse<Void> archivePlan(@PathVariable Long id){service.archivePlan(uid(),id);return ApiResponse.success();}
 @GetMapping("/records") public ApiResponse<List<LearningRecord>> records(@RequestParam(required=false)Long planId,@RequestParam(required=false)String keyword){return ApiResponse.success(service.listRecords(uid(),planId,keyword));}
 @PostMapping("/records") public ApiResponse<Long> createRecord(@Valid @RequestBody LearningRecordRequest r){return ApiResponse.success(service.createRecord(uid(),r));}
 @GetMapping("/summaries") public ApiResponse<List<LearningSummary>> summaries(@RequestParam(required=false)Long planId,@RequestParam(required=false)String type){return ApiResponse.success(service.listSummaries(uid(),planId,type));}
 @PostMapping("/summaries") public ApiResponse<Long> createSummary(@Valid @RequestBody LearningSummaryRequest r){return ApiResponse.success(service.createSummary(uid(),r));}
 @GetMapping("/growth-stats") public ApiResponse<GrowthStats> stats(){return ApiResponse.success(service.stats(uid()));}
}