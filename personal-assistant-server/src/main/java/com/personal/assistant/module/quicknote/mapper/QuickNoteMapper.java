package com.personal.assistant.module.quicknote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personal.assistant.module.quicknote.entity.QuickNote;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuickNoteMapper extends BaseMapper<QuickNote> {
}
