package com.personal.assistant.module.devlog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("personal_access_token")
public class PersonalAccessToken {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String tokenPrefix;
    private String tokenHash;
    private String scope;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime revokedAt;
    private LocalDateTime createdAt;
}
