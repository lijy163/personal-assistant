package com.personal.assistant.module.quicknote.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.quicknote.dto.QuickNoteCreateRequest;
import com.personal.assistant.module.quicknote.entity.QuickNote;
import com.personal.assistant.module.quicknote.mapper.QuickNoteMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class QuickNoteService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_ARCHIVED = "ARCHIVED";

    private final QuickNoteMapper quickNoteMapper;

    public QuickNoteService(QuickNoteMapper quickNoteMapper) {
        this.quickNoteMapper = quickNoteMapper;
    }

    @Transactional
    public Long create(Long userId, QuickNoteCreateRequest request) {
        QuickNote note = new QuickNote();
        note.setUserId(userId);
        note.setContent(request.content());
        note.setStatus(STATUS_PENDING);
        LocalDateTime now = LocalDateTime.now();
        note.setCreatedAt(now);
        note.setUpdatedAt(now);
        quickNoteMapper.insert(note);
        return note.getId();
    }

    public List<QuickNote> listPending(Long userId) {
        return quickNoteMapper.selectList(
                new LambdaQueryWrapper<QuickNote>()
                        .eq(QuickNote::getUserId, userId)
                        .eq(QuickNote::getStatus, STATUS_PENDING)
                        .orderByDesc(QuickNote::getCreatedAt));
    }

    @Transactional
    public void archive(Long userId, Long id) {
        QuickNote note = quickNoteMapper.selectById(id);
        if (note == null || !note.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "快速记录不存在");
        }
        note.setStatus(STATUS_ARCHIVED);
        note.setUpdatedAt(LocalDateTime.now());
        quickNoteMapper.updateById(note);
    }
}
