package com.personal.assistant.module.task.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data;
@Data @TableName("life_item_detail") public class LifeItemDetail { @TableId private Long taskId; private String category; }