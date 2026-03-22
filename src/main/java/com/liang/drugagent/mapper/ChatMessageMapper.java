package com.liang.drugagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.drugagent.domain.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
