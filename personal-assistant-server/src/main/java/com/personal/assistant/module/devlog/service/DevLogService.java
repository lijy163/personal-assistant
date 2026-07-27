package com.personal.assistant.module.devlog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.devlog.dto.DevLogIngestRequest;
import com.personal.assistant.module.devlog.dto.DevLogSummary;
import com.personal.assistant.module.devlog.entity.DevLog;
import com.personal.assistant.module.devlog.mapper.DevLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DevLogService {
    private final DevLogMapper devLogMapper;

    public DevLogService(DevLogMapper devLogMapper) {
        this.devLogMapper = devLogMapper;
    }

    @Transactional
    public Long ingest(Long userId, DevLogIngestRequest request) {
        DevLog existing = devLogMapper.selectOne(new LambdaQueryWrapper<DevLog>()
                .eq(DevLog::getUserId, userId)
                .eq(DevLog::getFingerprint, request.fingerprint()));
        if (existing != null) {
            return existing.getId();
        }
        LocalDateTime now = LocalDateTime.now();
        DevLog log = new DevLog();
        log.setUserId(userId);
        log.setFingerprint(request.fingerprint());
        log.setTitle(request.title().trim());
        log.setProjectName(request.projectName().trim());
        log.setRepository(request.repository());
        log.setBranchName(request.branchName());
        log.setCommitHash(request.commitHash());
        log.setTaskGoal(request.taskGoal());
        log.setCoreChanges(request.coreChanges());
        log.setTechnicalDecisions(request.technicalDecisions());
        log.setProblemSolution(request.problemSolution());
        log.setVerificationResult(request.verificationResult());
        log.setTags(request.tags());
        log.setSource(StringUtils.hasText(request.source()) ? request.source().trim().toUpperCase() : "CODEX");
        log.setOccurredAt(request.occurredAt() == null ? now : request.occurredAt());
        log.setMarkdownContent(request.markdownContent());
        log.setCreatedAt(now);
        log.setUpdatedAt(now);
        devLogMapper.insert(log);
        return log.getId();
    }

    public List<DevLogSummary> list(Long userId, String keyword, String projectName) {
        LambdaQueryWrapper<DevLog> query = new LambdaQueryWrapper<DevLog>()
                .eq(DevLog::getUserId, userId)
                .eq(StringUtils.hasText(projectName), DevLog::getProjectName, projectName)
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(DevLog::getTitle, keyword)
                        .or().like(DevLog::getTaskGoal, keyword)
                        .or().like(DevLog::getCoreChanges, keyword)
                        .or().like(DevLog::getTags, keyword))
                .orderByDesc(DevLog::getOccurredAt);
        return devLogMapper.selectList(query).stream().map(this::summary).toList();
    }

    public DevLog detail(Long userId, Long id) {
        DevLog log = devLogMapper.selectById(id);
        if (log == null || !log.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "开发记录不存在");
        }
        return log;
    }

    private DevLogSummary summary(DevLog log) {
        return new DevLogSummary(log.getId(), log.getTitle(), log.getProjectName(), log.getBranchName(),
                log.getCommitHash(), log.getTags(), log.getSource(), log.getOccurredAt(), log.getCreatedAt());
    }
}
