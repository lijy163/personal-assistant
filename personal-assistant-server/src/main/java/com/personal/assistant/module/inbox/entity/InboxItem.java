package com.personal.assistant.module.inbox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("inbox_item")
public class InboxItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String content;
    private String suggestedType;
    private String confirmedType;
    private BigDecimal confidence;
    private String reason;
    private String status;
    private String inputType;
    private String source;
    private String tags;
    private String remark;
    private LocalDateTime recordedAt;
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private List<InboxAttachment> attachments;
}