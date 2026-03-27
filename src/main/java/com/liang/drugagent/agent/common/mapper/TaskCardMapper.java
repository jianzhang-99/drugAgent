package com.liang.drugagent.agent.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.drugagent.agent.common.entity.TaskCard;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

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
