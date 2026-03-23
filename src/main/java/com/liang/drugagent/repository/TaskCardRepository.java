package com.liang.drugagent.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.liang.drugagent.domain.entity.TaskCard;
import com.liang.drugagent.domain.req.TaskCardQueryReq;
import com.liang.drugagent.domain.resp.TaskCardVO;
import com.liang.drugagent.domain.resp.TaskStatisticsVO;

import java.util.Optional;

/**
 * 任务卡片仓储接口。
 *
 * @author liangjiajian
 */
public interface TaskCardRepository {

    /**
     * 创建任务卡片。
     */
    TaskCard create(TaskCard taskCard);

    /**
     * 分页查询任务卡片。
     */
    IPage<TaskCardVO> queryTaskCards(TaskCardQueryReq req);

    /**
     * 获取任务统计信息。
     */
    TaskStatisticsVO getTaskStatistics();

    /**
     * 更新任务状态。
     */
    boolean updateStatus(String taskId, String status);

    /**
     * 更新任务进度。
     */
    boolean updateProgress(String taskId, Integer progress, String currentStep);

    /**
     * 根据ID获取任务卡片详情。
     */
    TaskCardVO findById(String taskId);

    /**
     * 删除任务卡片。
     */
    boolean delete(String taskId);
}
