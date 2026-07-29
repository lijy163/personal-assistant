package com.personal.assistant.module.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.blog.dto.BlogArchiveResponse;
import com.personal.assistant.module.blog.dto.BlogPostRequest;
import com.personal.assistant.module.blog.dto.BlogPostSummary;
import com.personal.assistant.module.blog.entity.BlogPost;
import com.personal.assistant.module.blog.mapper.BlogPostMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class BlogService {
    private final BlogPostMapper posts;

    public BlogService(BlogPostMapper posts) {
        this.posts = posts;
    }

    public List<BlogPost> adminList(Long userId, String status, String keyword) {
        return posts.selectList(new LambdaQueryWrapper<BlogPost>()
                .eq(BlogPost::getUserId, userId)
                .eq(StringUtils.hasText(status), BlogPost::getStatus, status)
                .and(StringUtils.hasText(keyword), query -> query.like(BlogPost::getTitle, keyword)
                        .or().like(BlogPost::getSummary, keyword))
                .orderByDesc(BlogPost::getUpdatedAt));
    }

    public BlogPost adminGet(Long userId, Long id) {
        return requireOwned(userId, id);
    }

    @Transactional
    public Long create(Long userId, BlogPostRequest request) {
        BlogPost post = new BlogPost();
        post.setUserId(userId);
        post.setStatus("DRAFT");
        post.setViewCount(0L);
        post.setCreatedAt(LocalDateTime.now());
        copy(post, request);
        posts.insert(post);
        return post.getId();
    }

    @Transactional
    public void update(Long userId, Long id, BlogPostRequest request) {
        BlogPost post = requireOwned(userId, id);
        copy(post, request);
        posts.updateById(post);
    }

    @Transactional
    public void publish(Long userId, Long id) {
        BlogPost post = requireOwned(userId, id);
        if (!StringUtils.hasText(post.getMarkdownContent())) throw validation("文章正文不能为空");
        post.setStatus("PUBLISHED");
        if (post.getPublishedAt() == null) post.setPublishedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        posts.updateById(post);
    }

    @Transactional
    public void unpublish(Long userId, Long id) {
        BlogPost post = requireOwned(userId, id);
        post.setStatus("DRAFT");
        post.setUpdatedAt(LocalDateTime.now());
        posts.updateById(post);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        posts.deleteById(requireOwned(userId, id).getId());
    }

    public BlogArchiveResponse publicArchive(String keyword, String category, String tag, String site) {
        List<BlogPost> all = posts.selectList(new LambdaQueryWrapper<BlogPost>()
                .eq(BlogPost::getStatus, "PUBLISHED")
                .eq(BlogPost::getSite, normalizeSite(site))
                .and(StringUtils.hasText(keyword), query -> query.like(BlogPost::getTitle, keyword)
                        .or().like(BlogPost::getSummary, keyword)
                        .or().like(BlogPost::getMarkdownContent, keyword))
                .eq(StringUtils.hasText(category), BlogPost::getCategory, category)
                .like(StringUtils.hasText(tag), BlogPost::getTags, tag)
                .orderByDesc(BlogPost::getPinned)
                .orderByDesc(BlogPost::getPublishedAt));
        List<BlogPost> published = posts.selectList(new LambdaQueryWrapper<BlogPost>()
                .eq(BlogPost::getStatus, "PUBLISHED").eq(BlogPost::getSite, normalizeSite(site)).orderByDesc(BlogPost::getPublishedAt));
        List<String> categories = published.stream().map(BlogPost::getCategory).filter(StringUtils::hasText).distinct().sorted().toList();
        List<String> tags = published.stream().flatMap(post -> splitTags(post.getTags())).distinct().sorted().toList();
        return new BlogArchiveResponse(all.stream().map(this::summary).toList(), categories, tags);
    }

    @Transactional
    public BlogPost publicGet(String slug, String site) {
        BlogPost post = posts.selectOne(new LambdaQueryWrapper<BlogPost>()
                .eq(BlogPost::getSlug, slug).eq(BlogPost::getStatus, "PUBLISHED")
                .eq(BlogPost::getSite, normalizeSite(site)));
        if (post == null) throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在或尚未发布");
        posts.update(null, new LambdaUpdateWrapper<BlogPost>().eq(BlogPost::getId, post.getId())
                .setSql("view_count = view_count + 1"));
        post.setViewCount(post.getViewCount() + 1);
        return post;
    }

    public BlogPost requirePublished(Long id) {
        BlogPost post = posts.selectById(id);
        if (post == null || !"PUBLISHED".equals(post.getStatus())) throw new BusinessException(ErrorCode.NOT_FOUND, "图片不存在");
        return post;
    }

    private void copy(BlogPost post, BlogPostRequest request) {
        post.setTitle(request.title().trim());
        String slug = StringUtils.hasText(request.slug()) ? normalizeSlug(request.slug()) : normalizeSlug(request.title());
        ensureUniqueSlug(slug, post.getId());
        post.setSlug(slug);
        post.setSummary(trimToNull(request.summary()));
        post.setMarkdownContent(request.markdownContent());
        post.setCoverUrl(validateCoverUrl(request.coverUrl()));
        post.setCategory(trimToNull(request.category()));
        post.setTags(joinTags(request.tags()));
        post.setPinned(Boolean.TRUE.equals(request.pinned()));
        post.setSite(normalizeSite(request.site()));
        post.setSeoTitle(trimToNull(request.seoTitle()));
        post.setSeoDescription(trimToNull(request.seoDescription()));
        post.setUpdatedAt(LocalDateTime.now());
    }

    private void ensureUniqueSlug(String slug, Long currentId) {
        Long count = posts.selectCount(new LambdaQueryWrapper<BlogPost>().eq(BlogPost::getSlug, slug)
                .ne(currentId != null, BlogPost::getId, currentId));
        if (count > 0) throw validation("文章路径已存在，请修改 slug");
    }

    private String normalizeSlug(String value) {
        String slug = Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFKC)
                .replaceAll("[^\\p{IsHan}a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (!StringUtils.hasText(slug)) slug = "post-" + System.currentTimeMillis();
        return slug.length() > 220 ? slug.substring(0, 220).replaceAll("-$", "") : slug;
    }

    private String joinTags(String tags) {
        return StringUtils.hasText(tags) ? String.join(",", splitTags(tags).toList()) : null;
    }

    private Stream<String> splitTags(String tags) {
        if (!StringUtils.hasText(tags)) return Stream.empty();
        Set<String> values = new LinkedHashSet<>(Arrays.stream(tags.split("[,，]"))
                .map(String::trim).filter(StringUtils::hasText).toList());
        return values.stream();
    }

    private BlogPost requireOwned(Long userId, Long id) {
        BlogPost post = posts.selectById(id);
        if (post == null || !userId.equals(post.getUserId())) throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
        return post;
    }

    private BlogPostSummary summary(BlogPost post) {
        return new BlogPostSummary(post.getId(), post.getTitle(), post.getSlug(), post.getSummary(), post.getCoverUrl(),
                post.getCategory(), post.getTags(), post.getSite(), Boolean.TRUE.equals(post.getPinned()), post.getViewCount(),
                post.getPublishedAt(), post.getUpdatedAt());
    }

    private String normalizeSite(String site) {
        return "RAIN7".equalsIgnoreCase(site) ? "RAIN7" : "WORK";
    }

    private String validateCoverUrl(String value) {
        String url = trimToNull(value);
        if (url == null || url.startsWith("/api/public/blog/assets/")) return url;
        try {
            java.net.URI uri = java.net.URI.create(url);
            if ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) return url;
        } catch (IllegalArgumentException ignored) {
        }
        throw validation("封面地址仅支持站内图片或 HTTP/HTTPS URL");
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
