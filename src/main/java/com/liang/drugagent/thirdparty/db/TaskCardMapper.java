package com.liang.drugagent.thirdparty.db;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.drugagent.thirdparty.db.entity.TaskCard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 任务卡片Mapper
 *
 * @author drug-agent-team
 */
@Mapper
public interface TaskCardMapper extends BaseMapper<TaskCard> {

    /**
     * 查询今日新增任务数
     */
    @Select("SELECT COUNT(*) FROM task_card WHERE DATE(created_at) = CURDATE() AND is_deleted = 0")
    int countTodayNew();

    /**
     * 查询今日完成任务数
     */
    @Select("SELECT COUNT(*) FROM task_card WHERE DATE(completed_at) = CURDATE() AND status = 'COMPLETED' AND is_deleted = 0")
    int countTodayCompleted();

    /**
     * 查询高风险待处理任务
     */
    @Select("SELECT * FROM task_card WHERE risk_level = 'HIGH' AND status != 'COMPLETED' AND is_deleted = 0 ORDER BY priority ASC, created_at DESC LIMIT 5")
    List<TaskCard> findTopHighRiskTasks();

    /**
     * 查询即将到期任务
     */
    @Select("SELECT * FROM task_card WHERE deadline IS NOT NULL AND deadline <= DATE_ADD(CURDATE(), INTERVAL 3 DAY) AND deadline >= CURDATE() AND status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED') AND is_deleted = 0 ORDER BY deadline ASC LIMIT 5")
    List<TaskCard> findUpcomingDeadlineTasks();
}
