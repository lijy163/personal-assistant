package com.personal.assistant.module.blog.dto;

import java.time.LocalDateTime;

public record BlogPostSummary(Long id, String title, String slug, String summary, String coverUrl,
                              String category, String tags, String site, boolean pinned, long viewCount,
                              LocalDateTime publishedAt, LocalDateTime updatedAt) {
}
