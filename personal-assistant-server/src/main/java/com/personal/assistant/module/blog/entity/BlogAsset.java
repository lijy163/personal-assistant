package com.personal.assistant.module.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("blog_asset")
public class BlogAsset {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long userId;
    private String originalName;
    private String storedName;
    private String contentType;
    private Long fileSize;
    private String storagePath;
    private String publicToken;
    private LocalDateTime createdAt;
}
