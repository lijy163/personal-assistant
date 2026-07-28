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

    private InboxItem item(Long id, Long userId) {
        InboxItem item = new InboxItem();
        item.setId(id);
        item.setUserId(userId);
        item.setStatus("PENDING");
        return item;
    }
}
