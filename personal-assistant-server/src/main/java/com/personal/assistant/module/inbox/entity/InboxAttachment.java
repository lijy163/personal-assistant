package com.personal.assistant.module.inbox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("inbox_attachment")
public class InboxAttachment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long inboxItemId;
    private Long userId;
    private String originalName;
    private String storedName;
    private String contentType;
    private Long fileSize;
    private String fileKind;
    private String storagePath;
    private LocalDateTime createdAt;
}
