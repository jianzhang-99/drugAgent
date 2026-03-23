package com.liang.drugagent.repository.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.liang.drugagent.domain.entity.TaskCard;
import com.liang.drugagent.domain.req.TaskCardQueryReq;
import com.liang.drugagent.domain.resp.TaskCardVO;
import com.liang.drugagent.domain.resp.TaskStatisticsVO;
import com.liang.drugagent.mapper.TaskCardMapper;
import com.liang.drugagent.repository.TaskCardRepository;
import org.springframework.stereotype.Repository;

/**
 * 任务卡片仓储实现。
 *
 * @author liangjiajian
 */
@Repository
public class TaskCardRepositoryImpl implements TaskCardRepository {

    private final TaskCardMapper taskCardMapper;

    public TaskCardRepositoryImpl(TaskCardMapper taskCardMapper) {
        this.taskCardMapper = taskCardMapper;
    }

    @Override
    public TaskCard create(TaskCard taskCard) {
        if (taskCard.getId() == null) {
            taskCard.setId(java.util.UUID.randomUUID().toString());
        }
        if (taskCard.getCreatedAt() == null) {
            taskCard.setCreatedAt(java.time.LocalDateTime.now());
        }
        taskCardMapper.insert(taskCard);
        return taskCard;
    }

    @Override
    public IPage<TaskCardVO> queryTaskCards(TaskCardQueryReq req) {
        // Delegate to service for complex query logic
        return null; // Will be implemented with service call
    }

    @Override
    public TaskStatisticsVO getTaskStatistics() {
        // Delegate to service for complex statistics logic
        return null; // Will be implemented with service call
    }

    @Override
    public boolean updateStatus(String taskId, String status) {
        TaskCard task = new TaskCard();
        task.setId(taskId);
        task.setStatus(status);
        return taskCardMapper.updateById(task) > 0;
    }

    @Override
    public boolean updateProgress(String taskId, Integer progress, String currentStep) {
        TaskCard task = new TaskCard();
        task.setId(taskId);
        task.setProgress(progress);
        if (currentStep != null) {
            task.setCurrentStep(currentStep);
        }
        return taskCardMapper.updateById(task) > 0;
    }

    @Override
    public TaskCardVO findById(String taskId) {
        // This needs proper conversion - simplified for now
        return null;
    }

    @Override
    public boolean delete(String taskId) {
        TaskCard task = new TaskCard();
        task.setId(taskId);
        task.setIsDeleted(1);
        return taskCardMapper.updateById(task) > 0;
    }
}
