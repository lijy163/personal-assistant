package com.personal.assistant.module.blog.controller;

import com.personal.assistant.common.response.ApiResponse;
import com.personal.assistant.module.blog.dto.BlogArchiveResponse;
import com.personal.assistant.module.blog.entity.BlogPost;
import com.personal.assistant.module.blog.service.BlogAssetService;
import com.personal.assistant.module.blog.service.BlogService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/public/blog")
public class BlogPublicController {
    private final BlogService blogs;
    private final BlogAssetService assets;

    public BlogPublicController(BlogService blogs, BlogAssetService assets) {
        this.blogs = blogs;
        this.assets = assets;
    }

    @GetMapping("/posts")
    public ApiResponse<BlogArchiveResponse> posts(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String category,
                                                  @RequestParam(required = false) String tag,
                                                  @RequestParam(defaultValue = "WORK") String site) {
        return ApiResponse.success(blogs.publicArchive(keyword, category, tag, site));
    }

    @GetMapping("/posts/{slug}")
    public ApiResponse<BlogPost> post(@PathVariable String slug, @RequestParam(defaultValue = "WORK") String site) { return ApiResponse.success(blogs.publicGet(slug, site)); }

    @GetMapping("/assets/{id}")
    public ResponseEntity<org.springframework.core.io.Resource> image(@PathVariable Long id) {
        BlogAssetService.Download download = assets.publicDownload(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(download.asset().getContentType()))
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .contentLength(download.asset().getFileSize()).body(download.resource());
    }
}
