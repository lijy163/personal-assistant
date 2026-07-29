package com.personal.assistant.module.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("blog_post")
public class BlogPost {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String slug;
    private String summary;
    private String markdownContent;
    private String coverUrl;
    private String category;
    private String tags;
    private String status;
    private String site;
    private Boolean pinned;
    private String seoTitle;
    private String seoDescription;
    private Long viewCount;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
