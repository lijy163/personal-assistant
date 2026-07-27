package com.personal.assistant.module.quicknote.service;

import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.module.quicknote.dto.QuickNoteCreateRequest;
import com.personal.assistant.module.quicknote.entity.QuickNote;
import com.personal.assistant.module.quicknote.mapper.QuickNoteMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuickNoteServiceTest {
    @Mock private QuickNoteMapper mapper;

    @Test
    void createSetsOwnerContentAndPendingStatus() {
        QuickNoteService service = new QuickNoteService(mapper);
        service.create(7L, new QuickNoteCreateRequest("记住这件事"));
        ArgumentCaptor<QuickNote> captor = ArgumentCaptor.forClass(QuickNote.class);
        verify(mapper).insert(captor.capture());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals("记住这件事", captor.getValue().getContent());
        assertEquals("PENDING", captor.getValue().getStatus());
    }

    @Test
    void archiveRejectsAnotherUsersNote() {
        QuickNote note = new QuickNote(); note.setId(1L); note.setUserId(8L);
        when(mapper.selectById(1L)).thenReturn(note);
        QuickNoteService service = new QuickNoteService(mapper);
        assertThrows(BusinessException.class, () -> service.archive(7L, 1L));
    }
}