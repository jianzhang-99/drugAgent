package com.liang.drugagent.agent.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.drugagent.agent.common.entity.OssConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * OSS配置 Mapper。
 */
@Mapper
public interface OssConfigMapper extends BaseMapper<OssConfig> {

    /**
     * 查询默认启用的OSS配置。
     */
    @Select("SELECT * FROM oss_config WHERE enabled = 1 AND is_default = 1 LIMIT 1")
    OssConfig findDefaultConfig();

    /**
     * 根据ID查询启用的配置。
     */
    @Select("SELECT * FROM oss_config WHERE id = #{id} AND enabled = 1 LIMIT 1")
    OssConfig findEnabledById(@Param("id") String id);
}
