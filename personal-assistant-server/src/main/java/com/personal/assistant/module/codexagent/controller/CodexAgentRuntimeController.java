package com.personal.assistant.module.codexagent.controller;

import com.personal.assistant.common.response.ApiResponse;
import com.personal.assistant.module.codexagent.dto.CodexAgentDtos.*;
import com.personal.assistant.module.codexagent.service.CodexTaskService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/codex-agent-runtime")
@PreAuthorize("hasAuthority('codex-agent:run')")
public class CodexAgentRuntimeController {
    private final CodexTaskService tasks;

    public CodexAgentRuntimeController(CodexTaskService tasks) {
        this.tasks = tasks;
    }

    @PostMapping("/heartbeat")
    public ApiResponse<Void> heartbeat() {
        return ApiResponse.success();
    }

    @PostMapping("/tasks/claim")
    public ApiResponse<ClaimedTask> claim(Authentication authentication) {
        return ApiResponse.success(tasks.claim(agentId(authentication)));
    }

    @PostMapping("/tasks/{id}/renew")
    public ApiResponse<LocalDateTime> renew(Authentication authentication, @PathVariable Long id,
                                            @Valid @RequestBody LeaseRequest request) {
        return ApiResponse.success(tasks.renew(agentId(authentication), id, request));
    }

    @PostMapping("/tasks/{id}/events")
    public ApiResponse<Void> event(Authentication authentication, @PathVariable Long id,
                                   @Valid @RequestBody EventRequest request) {
        tasks.addEvent(agentId(authentication), id, request);
        return ApiResponse.success();
    }

    @PostMapping("/tasks/{id}/complete")
    public ApiResponse<Void> complete(Authentication authentication, @PathVariable Long id,
                                      @Valid @RequestBody CompleteRequest request) {
        tasks.complete(agentId(authentication), id, request);
        return ApiResponse.success();
    }

    @PostMapping("/tasks/{id}/fail")
    public ApiResponse<Void> fail(Authentication authentication, @PathVariable Long id,
                                  @Valid @RequestBody FailRequest request) {
        tasks.fail(agentId(authentication), id, request);
        return ApiResponse.success();
    }

    private Long agentId(Authentication authentication) {
        return (Long) authentication.getCredentials();
    }
}
