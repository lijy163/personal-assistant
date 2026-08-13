package com.personal.assistant.module.codexagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personal.assistant.module.codexagent.entity.CodexTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CodexTaskMapper extends BaseMapper<CodexTask> {
    @Select("""
            select * from codex_task
            where agent_id = #{agentId}
              and (status = 'PENDING' or (status = 'RUNNING' and lease_expires_at < now()))
            order by requested_at
            limit 1
            for update skip locked
            """)
    CodexTask selectClaimableForUpdate(@Param("agentId") Long agentId);
}
