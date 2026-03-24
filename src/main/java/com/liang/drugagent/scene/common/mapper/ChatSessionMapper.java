package com.liang.drugagent.scene.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.drugagent.scene.common.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * ChatSession 数据库映射器。
 *
 * <p>继承MyBatis Plus的 {@link BaseMapper}，提供通用的CRUD操作。</p>
 *
 * @author liangjiajian
 * @see ChatSession
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
