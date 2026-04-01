package com.liang.drugagent.agent.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.drugagent.agent.common.entity.OssFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * OSS文件 Mapper。
 */
@Mapper
public interface OssFileMapper extends BaseMapper<OssFile> {

    /**
     * 根据业务ID查询文件列表。
     */
    @Select("SELECT * FROM oss_file WHERE biz_id = #{bizId} AND biz_type = #{bizType} AND is_deleted = 0 ORDER BY created_at DESC")
    List<OssFile> findByBizIdAndBizType(@Param("bizId") String bizId, @Param("bizType") String bizType);

    /**
     * 根据文件ID查询有效文件。
     */
    @Select("SELECT * FROM oss_file WHERE id = #{id} AND is_deleted = 0 LIMIT 1")
    OssFile findValidById(@Param("id") String id);

    /**
     * 标记文件为已删除（软删除）。
     */
    @Update("UPDATE oss_file SET is_deleted = 1, updated_at = NOW() WHERE id = #{id}")
    int markDeleted(@Param("id") String id);

    /**
     * 增加文件访问次数。
     */
    @Update("UPDATE oss_file SET access_count = access_count + 1 WHERE id = #{id}")
    int incrementAccessCount(@Param("id") String id);
}
