package com.personal.assistant.module.inbox.service;

import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.module.inbox.dto.InboxConfirmRequest;
import com.personal.assistant.module.inbox.dto.InboxCreateRequest;
import com.personal.assistant.module.inbox.entity.InboxItem;
import com.personal.assistant.module.inbox.mapper.InboxItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboxServiceTest {
    @Mock
    InboxItemMapper mapper;
    @Mock
    InboxAttachmentService attachments;
    InboxService service;

    @BeforeEach
    void setUp() {
        service = new InboxService(mapper, attachments);
    }

    @Test
    void suggestsExpenseButKeepsItemPending() {
        InboxItem result = service.create(7L, new InboxCreateRequest(" 午餐消费 35 元 "));

        assertEquals("EXPENSE", result.getSuggestedType());
        assertEquals("PENDING", result.getStatus());
        assertEquals("午餐消费 35 元", result.getContent());
        verify(mapper).insert(result);
    }

    @Test
    void confirmsOnlyAfterExplicitUserChoice() {
        InboxItem item = item(1L, 7L);
        when(mapper.selectById(1L)).thenReturn(item);

        service.confirm(7L, 1L, new InboxConfirmRequest("TASK"));

        ArgumentCaptor<InboxItem> captor = ArgumentCaptor.forClass(InboxItem.class);
        verify(mapper).updateById(captor.capture());
        assertEquals("TASK", captor.getValue().getConfirmedType());
        assertEquals("CONFIRMED", captor.getValue().getStatus());
    }

    @Test
    void rejectsAnotherUsersItem() {
        when(mapper.selectById(1L)).thenReturn(item(1L, 8L));

        assertThrows(BusinessException.class,
                () -> service.confirm(7L, 1L, new InboxConfirmRequest("NOTE")));
    }

    @Test
    void archivesSelectedPendingItems() {
        InboxItem first = item(1L, 7L);
        InboxItem second = item(2L, 7L);
        when(mapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(first, second));
        when(mapper.updateById(org.mockito.ArgumentMatchers.any(InboxItem.class))).thenReturn(1);

        int updated = service.archiveBatch(7L, List.of(1L, 2L));

        assertEquals(2, updated);
        assertEquals("ARCHIVED", first.getStatus());
        assertEquals("ARCHIVED", second.getStatus());
    }

    private InboxItem item(Long id, Long userId) {
        InboxItem item = new InboxItem();
        item.setId(id);
        item.setUserId(userId);
        item.setStatus("PENDING");
        return item;
    }
}
