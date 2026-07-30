package com.personal.assistant.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.ai.dto.AiAssistRequest;
import com.personal.assistant.module.ai.dto.AiConfigRequest;
import com.personal.assistant.module.ai.entity.AiInvocation;
import com.personal.assistant.module.ai.entity.AiProviderConfig;
import com.personal.assistant.module.ai.mapper.AiInvocationMapper;
import com.personal.assistant.module.ai.mapper.AiProviderConfigMapper;
import com.personal.assistant.module.ai.provider.AiProvider;
import com.personal.assistant.module.reminder.service.SecretCryptoService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AiGatewayService {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Set<String> SCENES = Set.of(
            "INBOX_STRUCTURE", "TASK_BREAKDOWN", "WEEKLY_SUMMARY", "KNOWLEDGE_QA", "CONTENT_TAGGING");
    private static final Map<String, String> SOURCE_TABLES = Map.of(
            "INBOX", "inbox_item",
            "TASK", "task_item",
            "REPORT", "generated_report",
            "KNOWLEDGE", "knowledge_entry");

    private final AiProviderConfigMapper configs;
    private final AiInvocationMapper invocations;
    private final SecretCryptoService crypto;
    private final JdbcTemplate jdbc;
    private final Map<String, AiProvider> providers;

    public AiGatewayService(
            AiProviderConfigMapper configs,
            AiInvocationMapper invocations,
            SecretCryptoService crypto,
            JdbcTemplate jdbc,
            List<AiProvider> providers) {
        this.configs = configs;
        this.invocations = invocations;
        this.crypto = crypto;
        this.jdbc = jdbc;
        this.providers = providers.stream().collect(Collectors.toMap(AiProvider::type, Function.identity()));
    }

    @Transactional
    public Long saveConfig(Long uid, AiConfigRequest request) {
        String providerType = request.providerType().toUpperCase();
        AiProviderConfig config = configs.selectOne(new LambdaQueryWrapper<AiProviderConfig>()
                .eq(AiProviderConfig::getUserId, uid)
                .eq(AiProviderConfig::getProviderType, providerType));
        boolean create = config == null;
        if (create) {
            config = new AiProviderConfig();
            config.setUserId(uid);
            config.setProviderType(providerType);
            config.setCreatedAt(now());
        }
        config.setBaseUrl(request.baseUrl());
        config.setModelName(request.modelName());
        config.setApiKeyEncrypted(crypto.encrypt(request.apiKey()));
        config.setEnabled(!Boolean.FALSE.equals(request.enabled()));
        config.setUpdatedAt(now());
        if (create) configs.insert(config);
        else configs.updateById(config);
        return config.getId();
    }

    public List<Map<String, Object>> configs(Long uid) {
        return configs.selectList(new LambdaQueryWrapper<AiProviderConfig>()
                        .eq(AiProviderConfig::getUserId, uid))
                .stream()
                .map(config -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("id", config.getId());
                    result.put("providerType", config.getProviderType());
                    result.put("baseUrl", config.getBaseUrl());
                    result.put("modelName", config.getModelName());
                    result.put("enabled", config.getEnabled());
                    result.put("apiKeyConfigured", true);
                    return result;
                })
                .toList();
    }

    @Transactional
    public AiInvocation assist(Long uid, AiAssistRequest request) {
        String scene = request.scene().toUpperCase();
        if (!SCENES.contains(scene)) throw validation("AI 场景不支持");
        Source source = validateSource(uid, request.sourceType(), request.sourceId());
        AiProviderConfig config = configs.selectOne(new LambdaQueryWrapper<AiProviderConfig>()
                .eq(AiProviderConfig::getUserId, uid)
                .eq(AiProviderConfig::getEnabled, true)
                .last("limit 1"));
        AiInvocation log = new AiInvocation();
        log.setUserId(uid);
        log.setScene(scene);
        log.setSourceType(source.type());
        log.setSourceId(source.id());
        log.setPromptVersion("v1");
        log.setRedactedInput(redact(request.input()));
        log.setStatus("RUNNING");
        log.setConfirmed(false);
        log.setCreatedAt(now());
        if (config != null) {
            log.setProviderConfigId(config.getId());
            log.setModelName(config.getModelName());
        }
        invocations.insert(log);
        try {
            if (config == null) throw new IllegalStateException("尚未配置可用 AI Provider");
            AiProvider provider = providers.get(config.getProviderType());
            if (provider == null) throw new IllegalStateException("AI Provider 未安装: " + config.getProviderType());
            log.setResultContent(provider.complete(
                    config, crypto.decrypt(config.getApiKeyEncrypted()), prompt(scene), log.getRedactedInput()));
            log.setStatus("PENDING_CONFIRMATION");
        } catch (Exception exception) {
            log.setStatus("FAILED");
            log.setFailureReason(exception.getMessage());
        }
        invocations.updateById(log);
        return log;
    }

    public List<AiInvocation> logs(Long uid) {
        return invocations.selectList(new LambdaQueryWrapper<AiInvocation>()
                .eq(AiInvocation::getUserId, uid)
                .orderByDesc(AiInvocation::getCreatedAt)
                .last("limit 200"));
    }

    @Transactional
    public void confirm(Long uid, Long id) {
        AiInvocation invocation = owned(uid, id);
        if (!"PENDING_CONFIRMATION".equals(invocation.getStatus())) throw validation("只有待确认结果可以确认");
        invocation.setConfirmed(true);
        invocation.setStatus("CONFIRMED");
        invocation.setConfirmedAt(now());
        invocations.updateById(invocation);
    }

    private Source validateSource(Long uid, String sourceType, Long sourceId) {
        boolean hasType = StringUtils.hasText(sourceType);
        if (hasType != (sourceId != null)) throw validation("AI 来源类型和记录 ID 必须同时提供");
        if (!hasType) return new Source(null, null);
        String normalizedType = sourceType.toUpperCase();
        String table = SOURCE_TABLES.get(normalizedType);
        if (table == null) throw validation("AI 来源类型不支持");
        Integer count = jdbc.queryForObject(
                "select count(*) from " + table + " where id=? and user_id=?", Integer.class, sourceId, uid);
        if (count == null || count == 0) throw new BusinessException(ErrorCode.NOT_FOUND, "AI 来源记录不存在");
        return new Source(normalizedType, sourceId);
    }

    private String redact(String input) {
        return input
                .replaceAll("(?<!\\d)1[3-9]\\d{9}(?!\\d)", "[手机号]")
                .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "[邮箱]")
                .replaceAll("(?<!\\d)\\d{17}[0-9Xx](?!\\d)", "[身份证号]")
                .replaceAll("(?<!\\d)\\d{16,19}(?!\\d)", "[银行卡号]");
    }

    private String prompt(String scene) {
        return switch (scene) {
            case "INBOX_STRUCTURE" -> "将输入整理为结构化候选字段，保持原意，只输出建议，不执行任何操作。";
            case "TASK_BREAKDOWN" -> "将目标拆为可执行步骤，给出优先级和预计耗时，结果需人工确认。";
            case "WEEKLY_SUMMARY" -> "总结本周事实、风险、收获和下一步行动，不编造数据。";
            case "KNOWLEDGE_QA" -> "仅依据输入知识回答，信息不足时明确说明。";
            default -> "为内容推荐少量准确标签，结果需人工确认。";
        };
    }

    private AiInvocation owned(Long uid, Long id) {
        AiInvocation invocation = invocations.selectById(id);
        if (invocation == null || !uid.equals(invocation.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "AI 调用记录不存在");
        }
        return invocation;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(SHANGHAI);
    }

    private BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    private record Source(String type, Long id) {
    }
}
