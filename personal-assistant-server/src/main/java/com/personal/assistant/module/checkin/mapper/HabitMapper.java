package com.personal.assistant.module.checkin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personal.assistant.module.checkin.entity.Habit;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HabitMapper extends BaseMapper<Habit> {
}
