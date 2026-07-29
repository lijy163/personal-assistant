package com.personal.assistant.module.blog.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BlogPostRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 220) String slug,
        @Size(max = 500) String summary,
        @NotBlank String markdownContent,
        @Size(max = 1000) String coverUrl,
        @Size(max = 100) String category,
        @Size(max = 500) String tags,
        Boolean pinned,
        @Size(max = 200) String seoTitle,
        @Size(max = 500) String seoDescription,
        @Pattern(regexp = "WORK|RAIN7") String site) {
}
