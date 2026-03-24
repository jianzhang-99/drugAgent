package com.liang.drugagent.thirdparty.db;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.drugagent.thirdparty.db.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
