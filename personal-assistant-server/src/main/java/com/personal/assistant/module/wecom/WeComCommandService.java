package com.personal.assistant.module.wecom;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.module.codexagent.entity.CodexTask;
import com.personal.assistant.module.codexagent.entity.CodexAgent;
import com.personal.assistant.module.codexagent.mapper.CodexTaskMapper;
import com.personal.assistant.module.codexagent.service.CodexAgentService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class WeComCommandService {
    private final WeComProperties properties;
    private final CodexTaskMapper tasks;
    private final CodexAgentService agents;
    private final WeComMessageService messages;

    public WeComCommandService(WeComProperties properties, CodexTaskMapper tasks,
                               CodexAgentService agents, WeComMessageService messages) {
        this.properties = properties;
        this.tasks = tasks;
        this.agents = agents;
        this.messages = messages;
    }

    @Transactional
    public void handle(WeComCryptoService.IncomingMessage message) {
        if (!"text".equalsIgnoreCase(message.messageType())) return;
        if (!properties.getAllowedUsers().contains(message.fromUser())) {
            messages.sendText(message.fromUser(), "当前微信账号未获授权。");
            return;
        }
        if (message.messageId() != null && !message.messageId().isBlank()
                && tasks.selectCount(new LambdaQueryWrapper<CodexTask>()
                .eq(CodexTask::getSource, "WECOM").eq(CodexTask::getExternalMessageId, message.messageId())) > 0) return;
        String content = message.content() == null ? "" : message.content().trim();
        if (content.startsWith("确认 ")) {
            confirm(message.fromUser(), content.substring(3).trim());
        } else if (content.startsWith("状态 ")) {
            status(message.fromUser(), content.substring(3).trim());
        } else if (content.startsWith("问 ")) {
            create(message, content.substring(2).trim(), "READ_ONLY", "PENDING");
        } else if (content.startsWith("改 ")) {
            create(message, content.substring(2).trim(), "WORKSPACE_WRITE", "WAITING_CONFIRMATION");
        } else {
            messages.sendText(message.fromUser(), help());
        }
    }

    private void create(WeComCryptoService.IncomingMessage message, String input, String permission, String status) {
        if (input.isBlank()) {
            messages.sendText(message.fromUser(), help());
            return;
        }
        CodexAgent agent = agents.requireActive(properties.getDefaultCodexAgentId());
        ParsedPrompt parsed = parsePrompt(input);
        LocalDateTime now = LocalDateTime.now();
        CodexTask task = new CodexTask();
        task.setUserId(agent.getUserId());
        task.setAgentId(properties.getDefaultCodexAgentId());
        task.setProjectKey(parsed.projectKey());
        task.setPrompt(parsed.prompt());
        task.setModel(agent.getModel());
        task.setReasoningEffort(agent.getReasoningEffort());
        task.setPermissionMode(permission);
        task.setStatus(status);
        task.setSource("WECOM");
        task.setExternalUserId(message.fromUser());
        task.setExternalMessageId(message.messageId());
        task.setRequestedAt(now);
        task.setUpdatedAt(now);
        try {
            tasks.insert(task);
        } catch (DuplicateKeyException ignored) {
            return;
        }
        if ("WAITING_CONFIRMATION".equals(status)) {
            messages.sendText(message.fromUser(), "写入任务 #" + task.getId() + " 等待确认。\n项目：" + parsed.projectKey()
                    + "\n回复：确认 " + task.getId());
        } else {
            messages.sendText(message.fromUser(), "只读任务 #" + task.getId() + " 已进入队列。\n项目：" + parsed.projectKey());
        }
    }

    private void confirm(String weComUser, String idText) {
        Long id = parseId(idText);
        CodexTask task = id == null ? null : tasks.selectById(id);
        if (task == null || !"WECOM".equals(task.getSource()) || !weComUser.equals(task.getExternalUserId())
                || !"WAITING_CONFIRMATION".equals(task.getStatus())) {
            messages.sendText(weComUser, "没有找到可确认的写入任务。");
            return;
        }
        task.setStatus("PENDING");
        task.setUpdatedAt(LocalDateTime.now());
        tasks.updateById(task);
        messages.sendText(weComUser, "任务 #" + id + " 已确认并进入执行队列。");
    }

    private void status(String weComUser, String idText) {
        Long id = parseId(idText);
        CodexTask task = id == null ? null : tasks.selectById(id);
        if (task == null || !"WECOM".equals(task.getSource()) || !weComUser.equals(task.getExternalUserId())) {
            messages.sendText(weComUser, "任务不存在。");
            return;
        }
        String detail = switch (task.getStatus()) {
            case "COMPLETED" -> task.getFinalResponse();
            case "FAILED" -> task.getErrorMessage();
            default -> "";
        };
        messages.sendText(weComUser, "任务 #" + id + "：" + task.getStatus()
                + (detail == null || detail.isBlank() ? "" : "\n" + detail));
    }

    private ParsedPrompt parsePrompt(String input) {
        String[] parts = input.split("\\s+", 2);
        if (parts.length == 2 && parts[0].matches("[A-Za-z0-9._-]{1,100}")) {
            return new ParsedPrompt(parts[0], parts[1]);
        }
        return new ParsedPrompt(properties.getDefaultProjectKey(), input);
    }

    private Long parseId(String value) {
        try { return Long.valueOf(value); } catch (NumberFormatException ignored) { return null; }
    }

    private String help() {
        return "Codex 助手命令：\n问 [项目] 问题\n改 [项目] 修改要求\n确认 任务ID\n状态 任务ID";
    }

    private record ParsedPrompt(String projectKey, String prompt) {
    }
}
