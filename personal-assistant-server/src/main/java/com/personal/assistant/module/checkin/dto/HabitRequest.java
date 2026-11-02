package com.personal.assistant.module.checkin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新建或修改打卡项。全部字段可选，修改时传空表示保持原值。
 */
public record HabitRequest(
        @NotBlank(message = "打卡项名称不能为空")
        @Size(max = 120, message = "打卡项名称不超过 120 字")
        String name,

        @Size(max = 16, message = "图标不超过 16 个字符")
        String icon,

        @Size(max = 20, message = "颜色取值不合法")
        String color,

        String category,

        @Size(max = 200, message = "备注不超过 200 字")
        String note,

        Boolean active,

        Integer sortOrder
) {
}
