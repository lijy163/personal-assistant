package com.personal.assistant.module.inbox.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.inbox.dto.InboxCollectRequest;
import com.personal.assistant.module.inbox.dto.InboxConfirmRequest;
import com.personal.assistant.module.inbox.dto.InboxCreateRequest;
import com.personal.assistant.module.inbox.entity.InboxItem;
import com.personal.assistant.module.inbox.mapper.InboxItemMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class InboxService {
    private static final Set<String> TYPES = Set.of("TASK", "REMINDER", "EXPENSE", "LEARNING", "NOTE");
    private final InboxItemMapper mapper;
    private final InboxAttachmentService attachments;

    public InboxService(InboxItemMapper mapper, InboxAttachmentService attachments) {
        this.mapper = mapper;
        this.attachments = attachments;
    }

    public List<InboxItem> list(Long uid, String status) {
        List<InboxItem> items = mapper.selectList(new LambdaQueryWrapper<InboxItem>().eq(InboxItem::getUserId, uid)
                .eq(StringUtils.hasText(status), InboxItem::getStatus, status)
                .orderByDesc(InboxItem::getCreatedAt).last("limit 200"));
        items.forEach(item -> item.setAttachments(attachments.list(uid, item.getId())));
        return items;
    }

    @Transactional
    public InboxItem create(Long uid, InboxCreateRequest request) {
        return createItem(uid, request.content(), null, null, "WEB", null, "TEXT");
    }

    @Transactional
    public InboxItem collect(Long uid, InboxCollectRequest request, List<MultipartFile> files) {
        boolean hasFiles = files != null && files.stream().anyMatch(file -> file != null && !file.isEmpty());
        if (!StringUtils.hasText(request.content()) && !hasFiles) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文字或附件至少填写一项");
        }
        String content = StringUtils.hasText(request.content()) ? request.content() : "附件记录";
        String inputType = detectInputType(files);
        InboxItem item = createItem(uid, content, request.tags(), request.remark(), request.source(), request.recordedAt(), inputType);
        attachments.store(uid, item.getId(), files);
        item.setAttachments(attachments.list(uid, item.getId()));
        return item;
    }

    @Transactional
    public void confirm(Long uid, Long id, InboxConfirmRequest request) {
        if (!TYPES.contains(request.confirmedType())) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "收件箱类型不合法");
        InboxItem item = require(uid, id);
        item.setConfirmedType(request.confirmedType());
        item.setStatus("CONFIRMED");
        item.setConfirmedAt(LocalDateTime.now());
        mapper.updateById(item);
    }

    @Transactional
    public void archive(Long uid, Long id) {
        InboxItem item = require(uid, id);
        item.setStatus("ARCHIVED");
        item.setConfirmedAt(LocalDateTime.now());
        mapper.updateById(item);
    }

    @Transactional
    public int archiveBatch(Long uid, List<Long> ids) {
        LocalDateTime now = LocalDateTime.now();
        int updated = 0;
        for (InboxItem item : mapper.selectList(new LambdaQueryWrapper<InboxItem>()
                .eq(InboxItem::getUserId, uid).in(InboxItem::getId, ids))) {
            if ("ARCHIVED".equals(item.getStatus())) continue;
            item.setStatus("ARCHIVED");
            item.setConfirmedAt(now);
            updated += mapper.updateById(item);
        }
        return updated;
    }

    private InboxItem createItem(Long uid, String content, String tags, String remark, String source,
                                 LocalDateTime recordedAt, String inputType) {
        Suggestion suggestion = suggest(content);
        InboxItem item = new InboxItem();
        item.setUserId(uid);
        item.setContent(content.trim());
        item.setSuggestedType(suggestion.type());
        item.setConfidence(suggestion.confidence());
        item.setReason(suggestion.reason());
        item.setStatus("PENDING");
        item.setInputType(inputType);
        item.setSource(StringUtils.hasText(source) ? source.trim().toUpperCase() : "WEB");
        item.setTags(tags);
        item.setRemark(remark);
        item.setRecordedAt(recordedAt == null ? LocalDateTime.now() : recordedAt);
        item.setCreatedAt(LocalDateTime.now());
        mapper.insert(item);
        return item;
    }

    private String detectInputType(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return "TEXT";
        boolean audio = files.stream().filter(file -> file != null && !file.isEmpty())
                .anyMatch(file -> file.getContentType() != null && file.getContentType().toLowerCase().startsWith("audio/"));
        boolean image = files.stream().filter(file -> file != null && !file.isEmpty())
                .anyMatch(file -> file.getContentType() != null && file.getContentType().toLowerCase().startsWith("image/"));
        if (audio) return "AUDIO";
        if (image) return "IMAGE";
        return "FILE";
    }

    private InboxItem require(Long uid, Long id) {
        InboxItem item = mapper.selectById(id);
        if (item == null || !uid.equals(item.getUserId())) throw new BusinessException(ErrorCode.NOT_FOUND, "收件箱记录不存在");
        return item;
    }

    private Suggestion suggest(String content) {
        String text = content.toLowerCase();
        if (text.matches(".*(¥|￥|元|消费|付款|买了|支出|报销).*")) return new Suggestion("EXPENSE", BigDecimal.valueOf(0.85), "识别到金额或消费关键词");
        if (text.matches(".*(提醒|闹钟|别忘|明天|后天|点钟|到期).*")) return new Suggestion("REMINDER", BigDecimal.valueOf(0.82), "识别到时间或提醒关键词");
        if (text.matches(".*(学习|阅读|课程|复习|笔记|知识).*")) return new Suggestion("LEARNING", BigDecimal.valueOf(0.78), "识别到学习关键词");
        if (text.matches(".*(完成|处理|待办|任务|需要|todo).*")) return new Suggestion("TASK", BigDecimal.valueOf(0.72), "识别到行动关键词");
        return new Suggestion("NOTE", BigDecimal.valueOf(0.55), "未识别到明确业务场景，建议作为普通记录");
    }

    private record Suggestion(String type, BigDecimal confidence, String reason) {
    }
}
