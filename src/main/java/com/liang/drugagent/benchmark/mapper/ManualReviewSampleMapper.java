package com.liang.drugagent.benchmark.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.drugagent.benchmark.entity.ManualReviewSample;
import org.apache.ibatis.annotations.Mapper;

/**
 * 人工抽检样本 Mapper。
 */
@Mapper
public interface ManualReviewSampleMapper extends BaseMapper<ManualReviewSample> {
}
