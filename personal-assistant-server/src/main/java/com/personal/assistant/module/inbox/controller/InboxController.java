package com.personal.assistant.module.inbox.controller;

import com.personal.assistant.common.response.ApiResponse;
import com.personal.assistant.common.security.SecurityContextHelper;
import com.personal.assistant.module.inbox.dto.InboxCollectRequest;
import com.personal.assistant.module.inbox.dto.InboxConfirmRequest;
import com.personal.assistant.module.inbox.dto.InboxCreateRequest;
import com.personal.assistant.module.inbox.entity.InboxItem;
import com.personal.assistant.module.inbox.service.InboxAttachmentService;
import com.personal.assistant.module.inbox.service.InboxService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/inbox")
public class InboxController {
    private final InboxService service;
    private final InboxAttachmentService attachments;

    public InboxController(InboxService service, InboxAttachmentService attachments) {
        this.service = service;
        this.attachments = attachments;
    }

    private Long uid() {
        return SecurityContextHelper.currentUserId();
    }

    @GetMapping
    public ApiResponse<List<InboxItem>> list(@RequestParam(required = false) String status) {
        return ApiResponse.success(service.list(uid(), status));
    }

    @PostMapping
    public ApiResponse<InboxItem> create(@Valid @RequestBody InboxCreateRequest request) {
        return ApiResponse.success(service.create(uid(), request));
    }

    @PostMapping(value = "/collect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<InboxItem> collect(@Valid @ModelAttribute InboxCollectRequest request,
                                         @RequestPart(required = false) List<MultipartFile> files) {
        return ApiResponse.success(service.collect(uid(), request, files));
    }

    @GetMapping("/attachments/{id}")
    public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable Long id) {
        InboxAttachmentService.Download download = attachments.download(uid(), id);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(download.attachment().getContentType());
        } catch (Exception ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.attachment().getOriginalName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentLength(download.attachment().getFileSize()).body(download.resource());
    }

    @PatchMapping("/{id}/confirm")
    public ApiResponse<Void> confirm(@PathVariable Long id, @Valid @RequestBody InboxConfirmRequest request) {
        service.confirm(uid(), id, request);
        return ApiResponse.success();
    }

    @PatchMapping("/{id}/archive")
    public ApiResponse<Void> archive(@PathVariable Long id) {
        service.archive(uid(), id);
        return ApiResponse.success();
    }
}