package com.personal.assistant.module.publiccodex;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.codexagent.entity.CodexAgent;
import com.personal.assistant.module.codexagent.entity.CodexTask;
import com.personal.assistant.module.codexagent.mapper.CodexTaskMapper;
import com.personal.assistant.module.codexagent.service.CodexAgentService;
import com.personal.assistant.module.publiccodex.PublicCodexDtos.AnswerResponse;
import com.personal.assistant.module.publiccodex.PublicCodexDtos.AskResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PublicCodexService {
    private static final Set<String> ACTIVE_STATUSES = Set.of("PENDING", "RUNNING");
    private final PublicCodexProperties properties;
    private final CodexAgentService agents;
    private final CodexTaskMapper tasks;
    private final ConcurrentHashMap<String, LocalDateTime> lastRequests = new ConcurrentHashMap<>();

    public PublicCodexService(PublicCodexProperties properties, CodexAgentService agents, CodexTaskMapper tasks) {
        this.properties = properties;
        this.agents = agents;
        this.tasks = tasks;
    }

    @Transactional
    public AskResponse ask(String sessionToken, String question) {
        requireEnabled();
        String sessionHash = sessionHash(sessionToken);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime allowedAt = lastRequests.get(sessionHash);
        if (allowedAt != null && now.isBefore(allowedAt)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "提问过于频繁，请稍后再试");
        }
        long activeTasks = tasks.selectCount(new LambdaQueryWrapper<CodexTask>()
                .eq(CodexTask::getSource, "PUBLIC")
                .eq(CodexTask::getExternalUserId, sessionHash)
                .in(CodexTask::getStatus, ACTIVE_STATUSES));
        if (activeTasks >= properties.getMaxActiveTasksPerSession()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "当前仍有问题正在回答，请等待完成");
        }
        long allActiveTasks = tasks.selectCount(new LambdaQueryWrapper<CodexTask>()
                .eq(CodexTask::getSource, "PUBLIC")
                .in(CodexTask::getStatus, ACTIVE_STATUSES));
        if (allActiveTasks >= properties.getMaxActiveTasks()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "当前提问人数较多，请稍后再试");
        }
        CodexAgent agent = agents.requireActive(properties.getAgentId());
        CodexTask task = new CodexTask();
        task.setUserId(agent.getUserId());
        task.setAgentId(agent.getId());
        task.setProjectKey(properties.getProjectKey());
        task.setPrompt(publicPrompt(question.trim()));
        task.setModel(agent.getModel());
        task.setReasoningEffort(agent.getReasoningEffort());
        task.setPermissionMode("READ_ONLY");
        task.setStatus("PENDING");
        task.setSource("PUBLIC");
        task.setExternalUserId(sessionHash);
        task.setRequestedAt(now);
        task.setUpdatedAt(now);
        tasks.insert(task);
        lastRequests.put(sessionHash, now.plusSeconds(Math.max(1, properties.getMinimumIntervalSeconds())));
        return new AskResponse(task.getId(), task.getStatus(), task.getRequestedAt());
    }

    public AnswerResponse answer(String sessionToken, Long taskId) {
        requireEnabled();
        CodexTask task = tasks.selectById(taskId);
        if (task == null || !"PUBLIC".equals(task.getSource())
                || !sessionHash(sessionToken).equals(task.getExternalUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "问答不存在");
        }
        return new AnswerResponse(task.getId(), originalQuestion(task.getPrompt()), task.getStatus(),
                task.getFinalResponse(), task.getErrorMessage(), task.getRequestedAt(), task.getFinishedAt());
    }

    private void requireEnabled() {
        if (!properties.isEnabled() || properties.getAgentId() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公开问答暂未开放");
        }
    }

    private String sessionHash(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{32,100}")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "无效的问答会话");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String publicPrompt(String question) {
        return "你正在公开问答环境中回答访客。只回答一般知识和用户提供的内容；不要尝试访问其他目录、系统配置、环境变量、密钥、网络服务或个人数据；不要修改文件或执行破坏性命令。\n\n访客问题：" + question;
    }

    private String originalQuestion(String prompt) {
        int separator = prompt.indexOf("访客问题：");
        return separator < 0 ? prompt : prompt.substring(separator + 5);
    }
}
