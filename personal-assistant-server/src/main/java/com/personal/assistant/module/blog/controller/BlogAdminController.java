package com.personal.assistant.module.blog.controller;

import com.personal.assistant.common.response.ApiResponse;
import com.personal.assistant.common.security.SecurityContextHelper;
import com.personal.assistant.module.blog.dto.BlogPostRequest;
import com.personal.assistant.module.blog.entity.BlogAsset;
import com.personal.assistant.module.blog.entity.BlogPost;
import com.personal.assistant.module.blog.service.BlogAssetService;
import com.personal.assistant.module.blog.service.BlogService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/blog/admin")
public class BlogAdminController {
    private final BlogService blogs;
    private final BlogAssetService assets;

    public BlogAdminController(BlogService blogs, BlogAssetService assets) {
        this.blogs = blogs;
        this.assets = assets;
    }

    private Long uid() { return SecurityContextHelper.currentUserId(); }

    @GetMapping("/posts")
    public ApiResponse<List<BlogPost>> list(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String site) {
        return ApiResponse.success(blogs.adminList(uid(), status, keyword, site));
    }

    @GetMapping("/posts/{id}")
    public ApiResponse<BlogPost> get(@PathVariable Long id) { return ApiResponse.success(blogs.adminGet(uid(), id)); }

    @PostMapping("/posts")
    public ApiResponse<Long> create(@Valid @RequestBody BlogPostRequest request) { return ApiResponse.success(blogs.create(uid(), request)); }

    @PutMapping("/posts/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody BlogPostRequest request) {
        blogs.update(uid(), id, request);
        return ApiResponse.success();
    }

    @PostMapping("/posts/{id}/publish")
    public ApiResponse<Void> publish(@PathVariable Long id) { blogs.publish(uid(), id); return ApiResponse.success(); }

    @PostMapping("/posts/{id}/unpublish")
    public ApiResponse<Void> unpublish(@PathVariable Long id) { blogs.unpublish(uid(), id); return ApiResponse.success(); }

    @DeleteMapping("/posts/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        assets.deleteForPost(uid(), id);
        blogs.delete(uid(), id);
        return ApiResponse.success();
    }

    @PostMapping(value = "/posts/{id}/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> upload(@PathVariable Long id, @RequestPart MultipartFile file) {
        BlogAsset asset = assets.store(uid(), id, file);
        return ApiResponse.success(Map.of("id", asset.getId(), "url", "/api/public/blog/assets/" + asset.getPublicToken()));
    }

    @GetMapping("/assets/{id}")
    public ResponseEntity<org.springframework.core.io.Resource> image(@PathVariable Long id) {
        return response(assets.adminDownload(uid(), id));
    }

    private ResponseEntity<org.springframework.core.io.Resource> response(BlogAssetService.Download download) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(download.asset().getContentType()))
                .contentLength(download.asset().getFileSize()).body(download.resource());
    }
}
