package com.personal.assistant.module.tradingreview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.tradingreview.dto.TradingRuleRequest;
import com.personal.assistant.module.tradingreview.entity.TradingRule;
import com.personal.assistant.module.tradingreview.mapper.TradingRuleMapper;
import com.personal.assistant.module.tradingreview.mapper.TradingLogMapper;
import com.personal.assistant.module.tradingreview.dto.TradingRuleStatsResponse;
import com.personal.assistant.module.tradingreview.entity.TradingLog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

@Service
public class TradingRuleService {
    private static final Set<String> CATEGORIES = Set.of("BUY", "SELL", "RISK", "POSITION");
    private final TradingRuleMapper rules;
    private final TradingLogMapper trades;

    public TradingRuleService(TradingRuleMapper rules, TradingLogMapper trades) { this.rules = rules; this.trades = trades; }

    public List<TradingRule> list(Long userId) {
        return rules.selectList(new LambdaQueryWrapper<TradingRule>().eq(TradingRule::getUserId, userId).orderByDesc(TradingRule::getUpdatedAt).orderByDesc(TradingRule::getId));
    }

    public List<TradingRuleStatsResponse> stats(Long userId) {
        List<TradingLog> rows = trades.selectList(new LambdaQueryWrapper<TradingLog>().eq(TradingLog::getUserId, userId).isNotNull(TradingLog::getRuleId));
        return rows.stream().collect(java.util.stream.Collectors.groupingBy(TradingLog::getRuleId)).entrySet().stream().map(entry -> {
            List<TradingLog> grouped = entry.getValue();
            return new TradingRuleStatsResponse(entry.getKey(), grouped.size(), grouped.stream().filter(row -> Boolean.TRUE.equals(row.getPlanned())).count(), grouped.stream().filter(row -> !"CLOSED".equals(row.getStatus())).count(), grouped.stream().filter(row -> "CLOSED".equals(row.getStatus())).count());
        }).toList();
    }

    @Transactional
    public Long save(Long userId, Long id, TradingRuleRequest request) {
        String category = request.category().trim().toUpperCase();
        if (!CATEGORIES.contains(category)) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "规则分类不支持");
        TradingRule rule = id == null ? new TradingRule() : owned(id, userId);
        if (id == null) { rule.setUserId(userId); rule.setVersion(1); rule.setCreatedAt(now()); } else rule.setVersion((rule.getVersion() == null ? 0 : rule.getVersion()) + 1);
        rule.setName(request.name().trim()); rule.setCategory(category); rule.setSummary(trim(request.summary())); rule.setBuyConditions(request.buyConditions().trim()); rule.setForbiddenConditions(trim(request.forbiddenConditions())); rule.setSellConditions(trim(request.sellConditions())); rule.setChecklist(trim(request.checklist())); rule.setMaxPosition(request.maxPosition()); rule.setEnabled(!Boolean.FALSE.equals(request.enabled())); rule.setUpdatedAt(now());
        if (id == null) rules.insert(rule); else rules.updateById(rule); return rule.getId();
    }

    @Transactional public void toggle(Long userId, Long id, boolean enabled) { TradingRule rule = owned(id, userId); rule.setEnabled(enabled); rule.setUpdatedAt(now()); rules.updateById(rule); }
    @Transactional public void delete(Long userId, Long id) { rules.deleteById(owned(id, userId)); }
    public TradingRule owned(Long id, Long userId) { TradingRule rule = rules.selectById(id); if (rule == null || !userId.equals(rule.getUserId())) throw new BusinessException(ErrorCode.NOT_FOUND, "交易规则不存在"); return rule; }
    private LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Shanghai")); }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
