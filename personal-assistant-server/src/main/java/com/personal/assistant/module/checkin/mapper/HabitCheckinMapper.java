package com.personal.assistant.module.checkin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personal.assistant.module.checkin.dto.CheckinDaySummary;
import com.personal.assistant.module.checkin.entity.HabitCheckin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface HabitCheckinMapper extends BaseMapper<HabitCheckin> {

    @Select("""
            select checkin_date as checkin_date, count(*) as done_count
            from habit_checkin
            where user_id = #{userId} and checkin_date between #{from} and #{to}
            group by checkin_date
            order by checkin_date
            """)
    List<CheckinDaySummary> countByDay(@Param("userId") Long userId,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    @Select("select count(distinct checkin_date) from habit_checkin where user_id = #{userId}")
    long countDistinctDays(@Param("userId") Long userId);
}
