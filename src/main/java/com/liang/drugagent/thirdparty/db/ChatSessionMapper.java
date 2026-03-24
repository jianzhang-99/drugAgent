package com.liang.drugagent.thirdparty.db;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.drugagent.thirdparty.db.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
