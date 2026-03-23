package com.liang.drugagent.integrations.storage;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.drugagent.integrations.storage.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
