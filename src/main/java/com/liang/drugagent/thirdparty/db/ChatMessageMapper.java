package com.liang.drugagent.thirdparty.db;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.drugagent.thirdparty.db.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    @Select("SELECT * FROM chat_message WHERE session_id = #{sessionId} ORDER BY created_at DESC LIMIT #{limit}")
    List<ChatMessage> findByConversationIdOrderByCreatedAtDesc(@Param("sessionId") String sessionId, @Param("limit") int limit);

    @Delete("DELETE FROM chat_message WHERE session_id = #{sessionId}")
    void deleteByConversationId(@Param("sessionId") String sessionId);
}
