package com.personal.assistant.module.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personal.assistant.module.activity.dto.ActivityDaySummary;
import com.personal.assistant.module.activity.dto.ActivityTagSummary;
import com.personal.assistant.module.activity.entity.ActivityLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ActivityLogMapper extends BaseMapper<ActivityLog> {

    @Select("""
            select log_date as log_date, count(*) as entry_count
            from activity_log
            where user_id = #{userId} and log_date between #{from} and #{to}
            group by log_date
            order by log_date
            """)
    List<ActivityDaySummary> countByDay(@Param("userId") Long userId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);

    @Select("""
            select trim(entry_tag) as tag, count(*) as tag_count
            from activity_log, unnest(string_to_array(coalesce(tags, ''), ',')) as entry_tag
            where user_id = #{userId}
              and log_date between #{from} and #{to}
              and trim(entry_tag) <> ''
            group by trim(entry_tag)
            order by count(*) desc, trim(entry_tag)
            """)
    List<ActivityTagSummary> countByTag(@Param("userId") Long userId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);
}
