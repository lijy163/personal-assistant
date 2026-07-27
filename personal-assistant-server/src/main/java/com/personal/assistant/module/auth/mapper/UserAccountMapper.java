package com.personal.assistant.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personal.assistant.module.auth.entity.UserAccount;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {
}
