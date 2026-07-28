package com.personal.assistant.module.inbox.service;

import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.module.inbox.entity.InboxAttachment;
import com.personal.assistant.module.inbox.mapper.InboxAttachmentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InboxAttachmentServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void storesAllowedMobilePhotoInsideUserDirectory() {
        InboxAttachmentMapper mapper = mock(InboxAttachmentMapper.class);
        InboxAttachmentService service = new InboxAttachmentService(mapper, tempDir.toString());
        MockMultipartFile photo = new MockMultipartFile("files", "camera.jpg", "image/jpeg", new byte[]{1, 2, 3});

        service.store(7L, 9L, List.of(photo));

        verify(mapper).insert(any(InboxAttachment.class));
    }

    @Test
    void rejectsExecutableContentType() {
        InboxAttachmentService service = new InboxAttachmentService(mock(InboxAttachmentMapper.class), tempDir.toString());
        MockMultipartFile executable = new MockMultipartFile("files", "bad.exe", "application/x-msdownload", new byte[]{1});

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.store(7L, 9L, List.of(executable)));

        assertEquals("参数校验失败", exception.getErrorCode().defaultMessage());
    }
}
