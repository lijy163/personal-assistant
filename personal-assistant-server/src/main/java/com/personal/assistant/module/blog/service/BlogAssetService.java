package com.personal.assistant.module.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.blog.entity.BlogAsset;
import com.personal.assistant.module.blog.entity.BlogPost;
import com.personal.assistant.module.blog.mapper.BlogAssetMapper;
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
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class BlogAssetService {
    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final BlogAssetMapper assets;
    private final BlogService blogs;
    private final Path root;

    public BlogAssetService(BlogAssetMapper assets, BlogService blogs,
                            @Value("${app.storage.path:/app}") String storagePath) {
        this.assets = assets;
        this.blogs = blogs;
        this.root = Path.of(storagePath).toAbsolutePath().normalize().resolve("files").resolve("blog");
    }

    public BlogAsset store(Long userId, Long postId, MultipartFile file) {
        BlogPost post = blogs.adminGet(userId, postId);
        if (file == null || file.isEmpty()) throw validation("请选择图片");
        if (file.getSize() > MAX_SIZE) throw validation("图片不能超过 10 MB");
        String contentType = file.getContentType() == null ? "" : file.getContentType().split(";")[0].toLowerCase(Locale.ROOT);
        if (!TYPES.contains(contentType)) throw validation("仅支持 JPEG、PNG、WebP 和 GIF 图片");
        String original = cleanName(file.getOriginalFilename());
        String stored = UUID.randomUUID() + extension(original, contentType);
        Path directory = root.resolve(String.valueOf(userId)).resolve(String.valueOf(post.getId())).normalize();
        Path target = directory.resolve(stored).normalize();
        ensureInside(target);
        try {
            Files.createDirectories(directory);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "博客图片保存失败");
        }
        BlogAsset asset = new BlogAsset();
        asset.setPostId(postId);
        asset.setUserId(userId);
        asset.setOriginalName(original);
        asset.setStoredName(stored);
        asset.setContentType(contentType);
        asset.setFileSize(file.getSize());
        asset.setStoragePath(target.toString());
        asset.setPublicToken(UUID.randomUUID().toString().replace("-", ""));
        asset.setCreatedAt(LocalDateTime.now());
        assets.insert(asset);
        return asset;
    }

    public Download adminDownload(Long userId, Long id) {
        BlogAsset asset = require(id);
        if (!userId.equals(asset.getUserId())) throw new BusinessException(ErrorCode.NOT_FOUND, "图片不存在");
        return download(asset);
    }

    public Download publicDownload(Long id) {
        BlogAsset asset = require(id);
        blogs.requirePublished(asset.getPostId());
        return download(asset);
    }

    public void deleteForPost(Long userId, Long postId) {
        blogs.adminGet(userId, postId);
        for (BlogAsset asset : assets.selectList(new LambdaQueryWrapper<BlogAsset>().eq(BlogAsset::getPostId, postId))) {
            try {
                Path path = Path.of(asset.getStoragePath()).toAbsolutePath().normalize();
                ensureInside(path);
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
        }
    }

    private Download download(BlogAsset asset) {
        try {
            Path path = Path.of(asset.getStoragePath()).toAbsolutePath().normalize();
            ensureInside(path);
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) throw new IOException("unreadable");
            return new Download(asset, resource);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片文件不存在");
        }
    }

    private BlogAsset require(Long id) {
        BlogAsset asset = assets.selectById(id);
        if (asset == null) throw new BusinessException(ErrorCode.NOT_FOUND, "图片不存在");
        return asset;
    }

    private String cleanName(String name) {
        String clean = name == null ? "image" : Path.of(name).getFileName().toString().replaceAll("[\\r\\n]", "_");
        return clean.length() > 255 ? clean.substring(clean.length() - 255) : clean;
    }

    private String extension(String name, String type) {
        int index = name.lastIndexOf('.');
        if (index >= 0 && name.length() - index <= 6) return name.substring(index).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]", "");
        return switch (type) { case "image/png" -> ".png"; case "image/webp" -> ".webp"; case "image/gif" -> ".gif"; default -> ".jpg"; };
    }

    private void ensureInside(Path path) {
        if (!path.startsWith(root)) throw new BusinessException(ErrorCode.FORBIDDEN, "非法图片路径");
    }

    private BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    public record Download(BlogAsset asset, Resource resource) {
    }
}
