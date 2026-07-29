package com.personal.assistant.module.blog.dto;

import java.util.List;

public record BlogArchiveResponse(List<BlogPostSummary> posts, List<String> categories, List<String> tags) {
}
