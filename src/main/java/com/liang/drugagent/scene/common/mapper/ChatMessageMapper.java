package com.liang.drugagent.scene.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.drugagent.scene.common.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * ChatMessage 数据库映射器。
 *
 * <p>继承MyBatis Plus的 {@link BaseMapper}，提供通用的CRUD操作，
 * 并封装了会话维度的消息查询和删除方法。</p>
 *
 * @author liangjiajian
 * @see ChatMessage
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 查询会话消息（按创建时间倒序）。
     *
     * @param sessionId 会话ID
     * @param limit 返回条数限制
     * @return 消息列表
     */
    List<ChatMessage> findByConversationIdOrderByCreatedAtDesc(@Param("sessionId") String sessionId, @Param("limit") int limit);

    /**
     * 删除会话的所有消息。
     *
     * @param sessionId 会话ID
     */
    void deleteByConversationId(@Param("sessionId") String sessionId);
}
