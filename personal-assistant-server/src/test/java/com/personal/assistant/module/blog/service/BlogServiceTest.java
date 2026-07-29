package com.personal.assistant.module.blog.service;

import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.module.blog.dto.BlogPostRequest;
import com.personal.assistant.module.blog.entity.BlogPost;
import com.personal.assistant.module.blog.mapper.BlogPostMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlogServiceTest {
    @Mock BlogPostMapper posts;
    BlogService service;

    @BeforeEach
    void setUp() {
        service = new BlogService(posts);
    }

    @Test
    void createsDraftWithNormalizedSlugAndTags() {
        when(posts.selectCount(any())).thenReturn(0L);
        service.create(7L, request("Spring Boot 3 实践", "", "Java， Spring,Java"));
        ArgumentCaptor<BlogPost> captor = ArgumentCaptor.forClass(BlogPost.class);
        verify(posts).insert(captor.capture());
        assertEquals("DRAFT", captor.getValue().getStatus());
        assertEquals("spring-boot-3-实践", captor.getValue().getSlug());
        assertEquals("Java,Spring", captor.getValue().getTags());
    }

    @Test
    void rejectsDuplicateSlug() {
        when(posts.selectCount(any())).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.create(7L, request("标题", "same", "")));
    }

    @Test
    void onlyReturnsPublishedArticleBySlug() {
        when(posts.selectOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.publicGet("draft"));
    }

    private BlogPostRequest request(String title, String slug, String tags) {
        return new BlogPostRequest(title, slug, "摘要", "# 正文", null, "技术", tags,
                false, null, null);
    }
}
