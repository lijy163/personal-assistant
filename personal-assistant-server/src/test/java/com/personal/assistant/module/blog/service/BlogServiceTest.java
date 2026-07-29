package com.personal.assistant.module.blog.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlogServiceTest {
    @Mock BlogPostMapper posts;
    BlogService service;

    @BeforeEach
    void setUp() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(BlogPostMapper.class);
        service = new BlogService(posts);
    }

    @Test
    void createsDraftWithNormalizedSlugTagsAndSite() {
        when(posts.selectCount(any())).thenReturn(0L);
        service.create(7L, request("Spring Boot 3 实践", "", "Java， Spring,Java", "RAIN7"));
        ArgumentCaptor<BlogPost> captor = ArgumentCaptor.forClass(BlogPost.class);
        verify(posts).insert(captor.capture());
        assertEquals("DRAFT", captor.getValue().getStatus());
        assertEquals("spring-boot-3-实践", captor.getValue().getSlug());
        assertEquals("Java,Spring", captor.getValue().getTags());
        assertEquals("RAIN7", captor.getValue().getSite());
    }

    @Test
    void publicArchiveFiltersByRequestedSite() {
        when(posts.selectList(any())).thenReturn(List.of());
        service.publicArchive(null, null, null, "RAIN7");
        ArgumentCaptor<LambdaQueryWrapper<BlogPost>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(posts, times(2)).selectList(captor.capture());
        captor.getAllValues().forEach(wrapper -> {
            assertTrue(wrapper.getSqlSegment().contains("site"));
            assertTrue(wrapper.getParamNameValuePairs().containsValue("RAIN7"));
        });
    }

    @Test
    void rejectsDuplicateSlug() {
        when(posts.selectCount(any())).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.create(7L, request("标题", "same", "", "WORK")));
    }

    @Test
    void onlyReturnsPublishedArticleBySlug() {
        when(posts.selectOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.publicGet("draft", "WORK"));
    }

    private BlogPostRequest request(String title, String slug, String tags, String site) {
        return new BlogPostRequest(title, slug, "摘要", "# 正文", null, "技术", tags,
                false, null, null, site);
    }
}
