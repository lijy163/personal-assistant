package com.personal.assistant.module.inbox.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.inbox.entity.InboxAttachment;
import com.personal.assistant.module.inbox.mapper.InboxAttachmentMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class InboxAttachmentService {
    public static final long MAX_FILE_SIZE = 15L * 1024 * 1024;
    public static final int MAX_FILES = 5;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif");
    private static final Set<String> AUDIO_TYPES = Set.of("audio/mpeg", "audio/mp4", "audio/wav", "audio/webm", "audio/ogg", "audio/x-m4a");
    private static final Set<String> FILE_TYPES = Set.of("application/pdf", "text/plain", "text/csv",
            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final InboxAttachmentMapper mapper;
    private final Path root;

    public InboxAttachmentService(InboxAttachmentMapper mapper,
                                  @Value("${app.storage.path:/app}") String storagePath) {
        this.mapper = mapper;
        this.root = Path.of(storagePath).toAbsolutePath().normalize().resolve("files").resolve("inbox");
    }

    public List<InboxAttachment> list(Long userId, Long inboxItemId) {
        return mapper.selectList(new LambdaQueryWrapper<InboxAttachment>()
                .eq(InboxAttachment::getUserId, userId)
                .eq(InboxAttachment::getInboxItemId, inboxItemId)
                .orderByAsc(InboxAttachment::getCreatedAt));
    }

    public void store(Long userId, Long inboxItemId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return;
        if (files.size() > MAX_FILES) throw validation("单次最多上传 " + MAX_FILES + " 个附件");
        Path directory = root.resolve(String.valueOf(userId)).resolve(String.valueOf(inboxItemId)).normalize();
        ensureInsideRoot(directory);
        try {
            Files.createDirectories(directory);
            for (MultipartFile file : files) storeOne(userId, inboxItemId, directory, file);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "附件保存失败");
        }
    }

    public Download download(Long userId, Long attachmentId) {
        InboxAttachment attachment = mapper.selectById(attachmentId);
        if (attachment == null || !userId.equals(attachment.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "附件不存在");
        }
        try {
            Path path = Path.of(attachment.getStoragePath()).toAbsolutePath().normalize();
            ensureInsideRoot(path);
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) throw new IOException("unreadable");
            return new Download(attachment, resource);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "附件文件不存在");
        }
    }

    private void storeOne(Long userId, Long inboxItemId, Path directory, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return;
        if (file.getSize() > MAX_FILE_SIZE) throw validation("单个附件不能超过 15 MB");
        String originalName = cleanName(file.getOriginalFilename());
        String contentType = normalizeType(file.getContentType());
        String kind = kind(contentType);
        String extension = extension(originalName);
        String storedName = UUID.randomUUID() + extension;
        Path target = directory.resolve(storedName).normalize();
        ensureInsideRoot(target);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        InboxAttachment attachment = new InboxAttachment();
        attachment.setInboxItemId(inboxItemId);
        attachment.setUserId(userId);
        attachment.setOriginalName(originalName);
        attachment.setStoredName(storedName);
        attachment.setContentType(contentType);
        attachment.setFileSize(file.getSize());
        attachment.setFileKind(kind);
        attachment.setStoragePath(target.toString());
        attachment.setCreatedAt(LocalDateTime.now());
        mapper.insert(attachment);
    }

    private String kind(String contentType) {
        if (IMAGE_TYPES.contains(contentType)) return "IMAGE";
        if (AUDIO_TYPES.contains(contentType)) return "AUDIO";
        if (FILE_TYPES.contains(contentType)) return "FILE";
        throw validation("不支持的附件类型：" + contentType);
    }

    private String normalizeType(String contentType) {
        return contentType == null ? "application/octet-stream" : contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
    }

    private String cleanName(String name) {
        String cleaned = name == null ? "attachment" : Path.of(name).getFileName().toString().replaceAll("[\\r\\n]", "_");
        return cleaned.length() > 255 ? cleaned.substring(cleaned.length() - 255) : cleaned;
    }

    private String extension(String name) {
        int index = name.lastIndexOf('.');
        if (index < 0 || name.length() - index > 12) return "";
        return name.substring(index).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]", "");
    }

    private void ensureInsideRoot(Path path) {
        if (!path.startsWith(root)) throw new BusinessException(ErrorCode.FORBIDDEN, "非法附件路径");
    }

    private BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    public record Download(InboxAttachment attachment, Resource resource) {
    }
}
