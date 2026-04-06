package com.liang.drugagent.shared.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.drugagent.shared.rag.entity.RagFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * RAG 文件关联 Mapper。
 *
 * <p>对应数据库表 {@code rag_file}，关联 OSS 文件与向量库 sourceId。</p>
 */
@Mapper
public interface RagFileMapper extends BaseMapper<RagFile> {

    @Select("SELECT * FROM rag_file WHERE oss_id = #{ossId} LIMIT 1")
    Optional<RagFile> findByOssId(@Param("ossId") String ossId);

    @Select("SELECT * FROM rag_file WHERE source_id = #{sourceId} LIMIT 1")
    Optional<RagFile> findBySourceId(@Param("sourceId") String sourceId);

    @Select("SELECT * FROM rag_file WHERE source_id = #{sourceId}")
    List<RagFile> listBySourceId(@Param("sourceId") String sourceId);
}
