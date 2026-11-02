package com.personal.assistant.module.checkin.dto;

/**
 * 删除打卡项的结果：已有打卡历史时改为停用，避免连带删掉同步生成的活动记录。
 */
public record HabitRemovalResult(boolean archived) {
}
