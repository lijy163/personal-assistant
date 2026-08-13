package com.personal.assistant.module.codexagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.codexagent.dto.CodexAgentDtos.AgentSummary;
import com.personal.assistant.module.codexagent.dto.CodexAgentDtos.CreateAgentRequest;
import com.personal.assistant.module.codexagent.dto.CodexAgentDtos.CreatedAgent;
import com.personal.assistant.module.codexagent.entity.CodexAgent;
import com.personal.assistant.module.codexagent.mapper.CodexAgentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
public class CodexAgentService {
    public static final String TOKEN_PREFIX = "pa_agent_";
    private final CodexAgentMapper agents;
    private final SecureRandom secureRandom = new SecureRandom();

    public CodexAgentService(CodexAgentMapper agents) {
        this.agents = agents;
    }

    @Transactional
    public CreatedAgent create(Long userId, CreateAgentRequest request) {
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String rawToken = TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        LocalDateTime now = LocalDateTime.now();
        CodexAgent agent = new CodexAgent();
        agent.setUserId(userId);
        agent.setName(request.name().trim());
        agent.setTokenPrefix(rawToken.substring(0, 18));
        agent.setTokenHash(hash(rawToken));
        agent.setStatus("OFFLINE");
        agent.setCreatedAt(now);
        agent.setUpdatedAt(now);
        agents.insert(agent);
        return new CreatedAgent(agent.getId(), agent.getName(), rawToken, agent.getCreatedAt());
    }

    public List<AgentSummary> list(Long userId) {
        LocalDateTime onlineCutoff = LocalDateTime.now().minusSeconds(45);
        return agents.selectList(new LambdaQueryWrapper<CodexAgent>()
                        .eq(CodexAgent::getUserId, userId)
                        .orderByDesc(CodexAgent::getCreatedAt))
                .stream().map(agent -> new AgentSummary(agent.getId(), agent.getName(), agent.getTokenPrefix(),
                        effectiveStatus(agent, onlineCutoff), agent.getLastSeenAt(), agent.getRevokedAt(), agent.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void revoke(Long userId, Long id) {
        CodexAgent agent = requireOwned(userId, id);
        if (agent.getRevokedAt() == null) {
            agent.setRevokedAt(LocalDateTime.now());
            agent.setStatus("REVOKED");
            agent.setUpdatedAt(LocalDateTime.now());
            agents.updateById(agent);
        }
    }

    @Transactional
    public CodexAgent authenticate(String rawToken) {
        if (rawToken == null || !rawToken.startsWith(TOKEN_PREFIX)) return null;
        CodexAgent agent = agents.selectOne(new LambdaQueryWrapper<CodexAgent>()
                .eq(CodexAgent::getTokenHash, hash(rawToken)));
        if (agent == null || agent.getRevokedAt() != null) return null;
        LocalDateTime now = LocalDateTime.now();
        agent.setStatus("ONLINE");
        agent.setLastSeenAt(now);
        agent.setUpdatedAt(now);
        agents.updateById(agent);
        return agent;
    }

    public CodexAgent requireOwned(Long userId, Long id) {
        CodexAgent agent = agents.selectById(id);
        if (agent == null || !userId.equals(agent.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Codex Agent 不存在");
        }
        return agent;
    }

    private String effectiveStatus(CodexAgent agent, LocalDateTime cutoff) {
        if (agent.getRevokedAt() != null) return "REVOKED";
        return agent.getLastSeenAt() != null && agent.getLastSeenAt().isAfter(cutoff) ? "ONLINE" : "OFFLINE";
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
