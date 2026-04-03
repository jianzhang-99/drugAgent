package com.liang.drugagent.agent.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.drugagent.agent.common.entity.TaskCard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 任务卡片Mapper。
 *
 * <p>继承MyBatis Plus的 {@link BaseMapper}，提供任务卡片通用的CRUD操作，
 * 并封装了业务相关的统计和查询方法。</p>
 *
 * @author liangjiajian
 * @see TaskCard
 */
@Mapper
public interface TaskCardMapper extends BaseMapper<TaskCard> {

    /**
     * 查询今日新增任务数。
     *
     * @return 今日新增任务数量
     */
    int countTodayNew();

    /**
     * 查询今日完成任务数。
     *
     * @return 今日完成任务数量
     */
    int countTodayCompleted();

    /**
     * 按状态分组统计数量（排除已删除）。
     *
     * @return 状态及其对应的任务数量列表
     */
    List<Map<String, Object>> countGroupByStatus();

    /**
     * 查询已完成任务按风险等级分组统计（排除已删除）。
     *
     * @return 风险等级及其对应的已完成任务数量列表
     */
    List<Map<String, Object>> countRiskGroupByLevelForCompleted();

    /**
     * 查询已完成任务的平均评分（排除已删除）。
     *
     * @return 平均评分
     */
    Double avgScoreForCompleted();

    /**
     * 查询高风险待处理任务。
     *
     * @return 高风险任务列表，按优先级排序
     */
    List<TaskCard> findTopHighRiskTasks();

    /**
     * 查询即将到期任务。
     *
     * @return 即将到期（通常指未来24小时内）的任务列表
     */
    List<TaskCard> findUpcomingDeadlineTasks();
}
