package com.personal.assistant.module.codexagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.codexagent.dto.CodexAgentDtos.*;
import com.personal.assistant.module.codexagent.entity.CodexAgent;
import com.personal.assistant.module.codexagent.entity.CodexTask;
import com.personal.assistant.module.codexagent.entity.CodexTaskEvent;
import com.personal.assistant.module.codexagent.mapper.CodexAgentMapper;
import com.personal.assistant.module.codexagent.mapper.CodexTaskEventMapper;
import com.personal.assistant.module.codexagent.mapper.CodexTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CodexTaskService {
    private static final Set<String> PERMISSION_MODES = Set.of("READ_ONLY", "WORKSPACE_WRITE");
    private static final int LEASE_MINUTES = 2;
    private final CodexTaskMapper tasks;
    private final CodexTaskEventMapper events;
    private final CodexAgentMapper agents;
    private final CodexAgentService agentService;

    public CodexTaskService(CodexTaskMapper tasks, CodexTaskEventMapper events, CodexAgentMapper agents,
                            CodexAgentService agentService) {
        this.tasks = tasks;
        this.events = events;
        this.agents = agents;
        this.agentService = agentService;
    }

    @Transactional
    public Long create(Long userId, CreateTaskRequest request) {
        agentService.requireOwned(userId, request.agentId());
        String permissionMode = request.permissionMode().trim().toUpperCase();
        if (!PERMISSION_MODES.contains(permissionMode)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "权限模式只支持 READ_ONLY 或 WORKSPACE_WRITE");
        }
        LocalDateTime now = LocalDateTime.now();
        CodexTask task = new CodexTask();
        task.setUserId(userId);
        task.setAgentId(request.agentId());
        task.setProjectKey(request.projectKey().trim());
        task.setPrompt(request.prompt().trim());
        task.setPermissionMode(permissionMode);
        task.setStatus("PENDING");
        task.setRequestedAt(now);
        task.setUpdatedAt(now);
        tasks.insert(task);
        return task.getId();
    }

    public List<TaskSummary> list(Long userId) {
        return tasks.selectList(new LambdaQueryWrapper<CodexTask>()
                        .eq(CodexTask::getUserId, userId)
                        .orderByDesc(CodexTask::getRequestedAt).last("limit 200"))
                .stream().map(this::summary).toList();
    }

    public List<TaskEventSummary> events(Long userId, Long taskId) {
        requireOwned(userId, taskId);
        return events.selectList(new LambdaQueryWrapper<CodexTaskEvent>()
                        .eq(CodexTaskEvent::getTaskId, taskId)
                        .orderByAsc(CodexTaskEvent::getCreatedAt).last("limit 1000"))
                .stream().map(event -> new TaskEventSummary(event.getId(), event.getEventType(),
                        event.getContent(), event.getCreatedAt())).toList();
    }

    @Transactional
    public void cancel(Long userId, Long taskId) {
        CodexTask task = requireOwned(userId, taskId);
        if (!"PENDING".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第一版只支持取消等待中的任务");
        }
        task.setStatus("CANCELLED");
        task.setLeaseId(null);
        task.setLeaseExpiresAt(null);
        task.setFinishedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        tasks.updateById(task);
    }

    @Transactional
    public ClaimedTask claim(Long agentId) {
        CodexTask task = tasks.selectClaimableForUpdate(agentId);
        if (task == null) return null;
        LocalDateTime now = LocalDateTime.now();
        String leaseId = UUID.randomUUID().toString();
        task.setStatus("RUNNING");
        task.setLeaseId(leaseId);
        task.setLeaseExpiresAt(now.plusMinutes(LEASE_MINUTES));
        if (task.getStartedAt() == null) task.setStartedAt(now);
        task.setUpdatedAt(now);
        tasks.updateById(task);
        return new ClaimedTask(task.getId(), leaseId, task.getLeaseExpiresAt(), task.getProjectKey(),
                task.getPrompt(), task.getPermissionMode());
    }

    @Transactional
    public LocalDateTime renew(Long agentId, Long taskId, LeaseRequest request) {
        CodexTask task = requireLease(agentId, taskId, request.leaseId());
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(LEASE_MINUTES);
        task.setLeaseExpiresAt(expiresAt);
        task.setUpdatedAt(LocalDateTime.now());
        tasks.updateById(task);
        return expiresAt;
    }

    @Transactional
    public void addEvent(Long agentId, Long taskId, EventRequest request) {
        requireLease(agentId, taskId, request.leaseId());
        CodexTaskEvent event = new CodexTaskEvent();
        event.setTaskId(taskId);
        event.setEventType(request.eventType().trim());
        event.setContent(request.content());
        event.setCreatedAt(LocalDateTime.now());
        events.insert(event);
    }

    @Transactional
    public void complete(Long agentId, Long taskId, CompleteRequest request) {
        CodexTask task = requireLease(agentId, taskId, request.leaseId());
        task.setStatus("COMPLETED");
        task.setThreadId(request.threadId());
        task.setFinalResponse(request.finalResponse());
        finish(task);
    }

    @Transactional
    public void fail(Long agentId, Long taskId, FailRequest request) {
        CodexTask task = requireLease(agentId, taskId, request.leaseId());
        task.setStatus("FAILED");
        task.setThreadId(request.threadId());
        task.setErrorMessage(request.errorMessage());
        finish(task);
    }

    private void finish(CodexTask task) {
        task.setLeaseId(null);
        task.setLeaseExpiresAt(null);
        task.setFinishedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        tasks.updateById(task);
    }

    private CodexTask requireLease(Long agentId, Long taskId, String leaseId) {
        CodexTask task = tasks.selectById(taskId);
        if (task == null || !agentId.equals(task.getAgentId()) || !"RUNNING".equals(task.getStatus())
                || !leaseId.equals(task.getLeaseId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "任务租约无效或已被重新领取");
        }
        return task;
    }

    private CodexTask requireOwned(Long userId, Long taskId) {
        CodexTask task = tasks.selectById(taskId);
        if (task == null || !userId.equals(task.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Codex 任务不存在");
        }
        return task;
    }

    private TaskSummary summary(CodexTask task) {
        CodexAgent agent = agents.selectById(task.getAgentId());
        return new TaskSummary(task.getId(), task.getAgentId(), agent == null ? "-" : agent.getName(),
                task.getProjectKey(), task.getPrompt(), task.getPermissionMode(), task.getStatus(), task.getThreadId(),
                task.getFinalResponse(), task.getErrorMessage(), task.getRequestedAt(), task.getStartedAt(),
                task.getFinishedAt(), task.getUpdatedAt());
    }
}
