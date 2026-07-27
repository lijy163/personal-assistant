package com.personal.assistant.module.devlog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dev_log")
public class DevLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String fingerprint;
    private String title;
    private String projectName;
    private String repository;
    private String branchName;
    private String commitHash;
    private String taskGoal;
    private String coreChanges;
    private String technicalDecisions;
    private String problemSolution;
    private String verificationResult;
    private String tags;
    private String source;
    private LocalDateTime occurredAt;
    private String markdownContent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
