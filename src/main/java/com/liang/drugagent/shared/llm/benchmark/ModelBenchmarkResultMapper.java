package com.liang.drugagent.shared.llm.benchmark;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * ModelBenchmarkResult 数据库映射器。
 *
 * <p>继承MyBatis Plus的 {@link BaseMapper}，提供通用的CRUD操作。</p>
 *
 * @author liangjiajian
 */
@Mapper
public interface ModelBenchmarkResultMapper extends BaseMapper<ModelBenchmarkResult> {
}
