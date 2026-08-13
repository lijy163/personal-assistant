package com.personal.assistant.module.codexagent.controller;

import com.personal.assistant.common.response.ApiResponse;
import com.personal.assistant.common.security.SecurityContextHelper;
import com.personal.assistant.module.codexagent.dto.CodexAgentDtos.*;
import com.personal.assistant.module.codexagent.service.CodexAgentService;
import com.personal.assistant.module.codexagent.service.CodexTaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/codex-agents")
public class CodexAgentManagementController {
    private final CodexAgentService agents;
    private final CodexTaskService tasks;

    public CodexAgentManagementController(CodexAgentService agents, CodexTaskService tasks) {
        this.agents = agents;
        this.tasks = tasks;
    }

    @PostMapping
    public ApiResponse<CreatedAgent> createAgent(@Valid @RequestBody CreateAgentRequest request) {
        return ApiResponse.success(agents.create(SecurityContextHelper.currentUserId(), request));
    }

    @GetMapping
    public ApiResponse<List<AgentSummary>> listAgents() {
        return ApiResponse.success(agents.list(SecurityContextHelper.currentUserId()));
    }

    @PatchMapping("/{id}/revoke")
    public ApiResponse<Void> revokeAgent(@PathVariable Long id) {
        agents.revoke(SecurityContextHelper.currentUserId(), id);
        return ApiResponse.success();
    }

    @PatchMapping("/{id}/model")
    public ApiResponse<Void> updateModel(@PathVariable Long id,
                                         @Valid @RequestBody UpdateAgentModelRequest request) {
        agents.updateModel(SecurityContextHelper.currentUserId(), id, request.model(), request.reasoningEffort());
        return ApiResponse.success();
    }

    @PostMapping("/tasks")
    public ApiResponse<Long> createTask(@Valid @RequestBody CreateTaskRequest request) {
        return ApiResponse.success(tasks.create(SecurityContextHelper.currentUserId(), request));
    }

    @GetMapping("/tasks")
    public ApiResponse<List<TaskSummary>> listTasks() {
        return ApiResponse.success(tasks.list(SecurityContextHelper.currentUserId()));
    }

    @GetMapping("/tasks/{id}/events")
    public ApiResponse<List<TaskEventSummary>> taskEvents(@PathVariable Long id) {
        return ApiResponse.success(tasks.events(SecurityContextHelper.currentUserId(), id));
    }

    @PostMapping("/tasks/{id}/cancel")
    public ApiResponse<Void> cancelTask(@PathVariable Long id) {
        tasks.cancel(SecurityContextHelper.currentUserId(), id);
        return ApiResponse.success();
    }
}
