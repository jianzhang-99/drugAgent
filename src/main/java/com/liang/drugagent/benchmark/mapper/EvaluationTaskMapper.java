package com.liang.drugagent.benchmark.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.drugagent.benchmark.entity.EvaluationTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评测任务数据库映射器。
 *
 * <p>继承MyBatis Plus的 {@link BaseMapper}，提供通用的CRUD操作。</p>
 */
@Mapper
public interface EvaluationTaskMapper extends BaseMapper<EvaluationTask> {
}
