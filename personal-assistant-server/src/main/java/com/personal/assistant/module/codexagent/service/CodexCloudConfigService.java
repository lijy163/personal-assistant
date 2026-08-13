package com.personal.assistant.module.codexagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.codexagent.entity.CodexAgent;
import com.personal.assistant.module.reminder.service.SecretCryptoService;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class CodexCloudConfigService {
    private final JdbcTemplate jdbc;
    private final SecretCryptoService crypto;
    private final CodexAgentService agents;
    private final ObjectMapper objectMapper;
    private final Path runtimeDirectory;

    public CodexCloudConfigService(JdbcTemplate jdbc, SecretCryptoService crypto, CodexAgentService agents,
                                   ObjectMapper objectMapper,
                                   @Value("${app.codex-runtime.path:/app/codex-runtime}") String runtimePath) {
        this.jdbc = jdbc;
        this.crypto = crypto;
        this.agents = agents;
        this.objectMapper = objectMapper;
        this.runtimeDirectory = Path.of(runtimePath);
    }

    public record ConfigView(Long managementAgentId, Long publicAgentId, String baseUrl, boolean publicEnabled,
                             boolean managementTokenConfigured, boolean publicTokenConfigured,
                             boolean apiKeyConfigured) {
    }

    public record SaveRequest(Long managementAgentId, @Size(max = 200) String managementToken,
                              Long publicAgentId, @Size(max = 200) String publicToken,
                              @Size(max = 500) String apiKey, @Size(max = 500) String baseUrl,
                              boolean publicEnabled) {
    }

    private record StoredConfig(Long userId, Long managementAgentId, String managementTokenEncrypted,
                                Long publicAgentId, String publicTokenEncrypted, String apiKeyEncrypted,
                                String baseUrl, boolean publicEnabled) {
    }

    public ConfigView get(Long userId) {
        StoredConfig config = find(userId);
        if (config == null) return new ConfigView(null, null, "https://www.xshoow.cloud/v1", false,
                false, false, false);
        return view(config);
    }

    @Transactional
    public ConfigView save(Long userId, SaveRequest request) {
        StoredConfig existing = find(userId);
        String managementToken = secret(request.managementToken(), existing == null ? null : existing.managementTokenEncrypted());
        String publicToken = secret(request.publicToken(), existing == null ? null : existing.publicTokenEncrypted());
        String apiKey = secret(request.apiKey(), existing == null ? null : existing.apiKeyEncrypted());
        String baseUrl = normalizeBaseUrl(request.baseUrl());

        validateAgent(userId, request.managementAgentId(), managementToken, "云服务器 Agent");
        if (request.publicEnabled()) validateAgent(userId, request.publicAgentId(), publicToken, "公开问答 Agent");
        if ((request.managementAgentId() != null || request.publicEnabled()) && apiKey == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写 XSHOOW API Key");
        }

        String managementEncrypted = encryptOrNull(managementToken);
        String publicEncrypted = encryptOrNull(publicToken);
        String apiEncrypted = encryptOrNull(apiKey);
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            jdbc.update("""
                    insert into codex_cloud_config(user_id,management_agent_id,management_token_encrypted,
                    public_agent_id,public_token_encrypted,api_key_encrypted,base_url,public_enabled,created_at,updated_at)
                    values(?,?,?,?,?,?,?,?,?,?)
                    """, userId, request.managementAgentId(), managementEncrypted, request.publicAgentId(),
                    publicEncrypted, apiEncrypted, baseUrl, request.publicEnabled(), now, now);
        } else {
            jdbc.update("""
                    update codex_cloud_config set management_agent_id=?,management_token_encrypted=?,
                    public_agent_id=?,public_token_encrypted=?,api_key_encrypted=?,base_url=?,public_enabled=?,updated_at=?
                    where user_id=?
                    """, request.managementAgentId(), managementEncrypted, request.publicAgentId(), publicEncrypted,
                    apiEncrypted, baseUrl, request.publicEnabled(), now, userId);
        }
        StoredConfig saved = find(userId);
        sync(saved);
        return view(saved);
    }

    public Long publicAgentId() {
        return jdbc.query("select * from codex_cloud_config where public_enabled=true order by updated_at desc limit 1",
                (result, row) -> map(result)).stream().findFirst().map(StoredConfig::publicAgentId).orElse(null);
    }

    public boolean publicEnabled() {
        return publicAgentId() != null;
    }

    public boolean configured() {
        Integer count = jdbc.queryForObject("select count(*) from codex_cloud_config", Integer.class);
        return count != null && count > 0;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restoreRuntimeFiles() {
        try {
            List<StoredConfig> configs = jdbc.query("select * from codex_cloud_config", (result, row) -> map(result));
            configs.forEach(this::syncQuietly);
        } catch (Exception ignored) {
            // Liquibase may not have created the table yet during early bean initialization.
        }
    }

    private StoredConfig find(Long userId) {
        return jdbc.query("select * from codex_cloud_config where user_id=?", (result, row) -> map(result), userId)
                .stream().findFirst().orElse(null);
    }

    private StoredConfig map(ResultSet result) throws java.sql.SQLException {
        return new StoredConfig(result.getLong("user_id"), nullableLong(result, "management_agent_id"),
                result.getString("management_token_encrypted"), nullableLong(result, "public_agent_id"),
                result.getString("public_token_encrypted"), result.getString("api_key_encrypted"),
                result.getString("base_url"), result.getBoolean("public_enabled"));
    }

    private Long nullableLong(ResultSet result, String column) throws java.sql.SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private void validateAgent(Long userId, Long agentId, String token, String label) {
        if (agentId == null || token == null) throw new BusinessException(ErrorCode.VALIDATION_ERROR, label + " 配置不完整");
        CodexAgent agent = agents.requireOwned(userId, agentId);
        if (agent.getRevokedAt() != null) throw new BusinessException(ErrorCode.VALIDATION_ERROR, label + " 已撤销");
        if (!token.startsWith(CodexAgentService.TOKEN_PREFIX)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, label + " 令牌格式错误");
        }
        agents.requireTokenMatches(agentId, token);
    }

    private String secret(String input, String encryptedExisting) {
        if (input != null && !input.isBlank()) return input.trim();
        return encryptedExisting == null ? null : crypto.decrypt(encryptedExisting);
    }

    private String encryptOrNull(String value) {
        return value == null ? null : crypto.encrypt(value);
    }

    private String normalizeBaseUrl(String value) {
        String url = value == null || value.isBlank() ? "https://www.xshoow.cloud/v1" : value.trim();
        if (!url.matches("https://[A-Za-z0-9._:-]+(?:/[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=-]*)?")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "API 服务地址必须是 HTTPS 地址");
        }
        return url.replaceAll("/+$", "");
    }

    private ConfigView view(StoredConfig config) {
        return new ConfigView(config.managementAgentId(), config.publicAgentId(), config.baseUrl(),
                config.publicEnabled(), config.managementTokenEncrypted() != null,
                config.publicTokenEncrypted() != null, config.apiKeyEncrypted() != null);
    }

    private void sync(StoredConfig config) {
        try {
            Files.createDirectories(runtimeDirectory);
            write("management.json", config.managementAgentId() != null,
                    decrypt(config.managementTokenEncrypted()), decrypt(config.apiKeyEncrypted()), config.baseUrl());
            write("public.json", config.publicEnabled() && config.publicAgentId() != null,
                    decrypt(config.publicTokenEncrypted()), decrypt(config.apiKeyEncrypted()), config.baseUrl());
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "云端 Agent 配置已保存，但运行配置同步失败");
        }
    }

    private void syncQuietly(StoredConfig config) {
        try { sync(config); } catch (Exception ignored) { }
    }

    private String decrypt(String encrypted) {
        return encrypted == null ? null : crypto.decrypt(encrypted);
    }

    private void write(String filename, boolean enabled, String token, String apiKey, String baseUrl) throws Exception {
        Path target = runtimeDirectory.resolve(filename);
        Path temporary = runtimeDirectory.resolve(filename + ".tmp");
        objectMapper.writeValue(temporary.toFile(), Map.of(
                "enabled", enabled, "token", token == null ? "" : token,
                "apiKey", apiKey == null ? "" : apiKey, "baseUrl", baseUrl));
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
