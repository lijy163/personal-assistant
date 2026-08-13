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
import com.personal.assistant.module.wecom.WeComMessageService;

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
    private final WeComMessageService weComMessages;

    public CodexTaskService(CodexTaskMapper tasks, CodexTaskEventMapper events, CodexAgentMapper agents,
                            CodexAgentService agentService, WeComMessageService weComMessages) {
        this.tasks = tasks;
        this.events = events;
        this.agents = agents;
        this.agentService = agentService;
        this.weComMessages = weComMessages;
    }

    @Transactional
    public Long create(Long userId, CreateTaskRequest request) {
        CodexAgent taskAgent = agentService.requireOwned(userId, request.agentId());
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
        task.setModel(taskAgent.getModel());
        task.setReasoningEffort(taskAgent.getReasoningEffort());
        task.setPermissionMode(permissionMode);
        task.setStatus("PENDING");
        task.setSource("WEB");
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
        if ("RUNNING".equals(task.getStatus())) {
            task.setStatus("CANCEL_REQUESTED");
            task.setUpdatedAt(LocalDateTime.now());
            tasks.updateById(task);
            return;
        }
        if ("CANCEL_REQUESTED".equals(task.getStatus()) || "CANCELLED".equals(task.getStatus())) {
            return;
        }
        if (!"PENDING".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "只有等待中或执行中的任务可以终止");
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
                task.getPrompt(), task.getPermissionMode(), task.getModel(), task.getReasoningEffort());
    }

    @Transactional
    public LocalDateTime renew(Long agentId, Long taskId, LeaseRequest request) {
        CodexTask task = requireActiveLease(agentId, taskId, request.leaseId());
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(LEASE_MINUTES);
        task.setLeaseExpiresAt(expiresAt);
        task.setUpdatedAt(LocalDateTime.now());
        tasks.updateById(task);
        return expiresAt;
    }

    public TaskControl control(Long agentId, Long taskId, LeaseRequest request) {
        CodexTask task = requireActiveLease(agentId, taskId, request.leaseId());
        return new TaskControl("CANCEL_REQUESTED".equals(task.getStatus()));
    }

    @Transactional
    public void cancelled(Long agentId, Long taskId, LeaseRequest request) {
        CodexTask task = requireActiveLease(agentId, taskId, request.leaseId());
        if (!"CANCEL_REQUESTED".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "任务未请求终止");
        }
        task.setStatus("CANCELLED");
        finish(task);
    }

    @Transactional
    public void addEvent(Long agentId, Long taskId, EventRequest request) {
        requireActiveLease(agentId, taskId, request.leaseId());
        CodexTaskEvent event = new CodexTaskEvent();
        event.setTaskId(taskId);
        event.setEventType(request.eventType().trim());
        event.setContent(request.content());
        event.setCreatedAt(LocalDateTime.now());
        events.insert(event);
    }

    @Transactional
    public void complete(Long agentId, Long taskId, CompleteRequest request) {
        CodexTask task = requireActiveLease(agentId, taskId, request.leaseId());
        if ("CANCEL_REQUESTED".equals(task.getStatus())) {
            task.setStatus("CANCELLED");
            finish(task);
            return;
        }
        task.setStatus("COMPLETED");
        task.setThreadId(request.threadId());
        task.setFinalResponse(request.finalResponse());
        finish(task);
        notifyWeCom(task, "Codex 任务 #" + task.getId() + " 已完成\n项目：" + task.getProjectKey()
                + "\n\n" + request.finalResponse());
    }

    @Transactional
    public void fail(Long agentId, Long taskId, FailRequest request) {
        CodexTask task = requireActiveLease(agentId, taskId, request.leaseId());
        if ("CANCEL_REQUESTED".equals(task.getStatus())) {
            task.setStatus("CANCELLED");
            finish(task);
            return;
        }
        task.setStatus("FAILED");
        task.setThreadId(request.threadId());
        task.setErrorMessage(request.errorMessage());
        finish(task);
        notifyWeCom(task, "Codex 任务 #" + task.getId() + " 执行失败\n项目：" + task.getProjectKey()
                + "\n\n" + request.errorMessage());
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

    private CodexTask requireActiveLease(Long agentId, Long taskId, String leaseId) {
        CodexTask task = tasks.selectById(taskId);
        if (task == null || !agentId.equals(task.getAgentId())
                || !("RUNNING".equals(task.getStatus()) || "CANCEL_REQUESTED".equals(task.getStatus()))
                || !leaseId.equals(task.getLeaseId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "任务租约无效或已结束");
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
                task.getProjectKey(), task.getPrompt(), task.getModel(), task.getReasoningEffort(), task.getPermissionMode(), task.getStatus(), task.getThreadId(),
                task.getFinalResponse(), task.getErrorMessage(), task.getRequestedAt(), task.getStartedAt(),
                task.getFinishedAt(), task.getUpdatedAt());
    }

    private void notifyWeCom(CodexTask task, String content) {
        if ("WECOM".equals(task.getSource()) && task.getExternalUserId() != null) {
            try {
                weComMessages.sendText(task.getExternalUserId(), content);
            } catch (Exception ignored) {
                // 任务结果已经持久化，通知失败不应回滚任务状态。
            }
        }
    }
}
