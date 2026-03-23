package com.liang.drugagent.application.task;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.liang.drugagent.domain.entity.TaskCard;
import com.liang.drugagent.domain.req.TaskCardQueryReq;
import com.liang.drugagent.domain.resp.TaskCardVO;
import com.liang.drugagent.domain.resp.TaskStatisticsVO;
import com.liang.drugagent.service.TaskCardService;
import org.springframework.stereotype.Service;

/**
 * 任务看板应用服务。
 *
 * @author liangjiajian
 */
@Service
public class TaskBoardApplicationService {

    private final TaskCardService taskCardService;

    public TaskBoardApplicationService(TaskCardService taskCardService) {
        this.taskCardService = taskCardService;
    }

    public IPage<TaskCardVO> queryTasks(TaskCardQueryReq req) {
        return taskCardService.queryTaskCards(req);
    }

    public TaskStatisticsVO getStatistics() {
        return taskCardService.getTaskStatistics();
    }

    public TaskCard createTask(TaskCard task) {
        return taskCardService.createTaskCard(task);
    }

    public boolean updateTaskStatus(String taskId, String status) {
        return taskCardService.updateTaskStatus(taskId, status);
    }

    public boolean updateTaskProgress(String taskId, Integer progress, String currentStep) {
        return taskCardService.updateTaskProgress(taskId, progress, currentStep);
    }

    public TaskCardVO getTaskById(String id) {
        return taskCardService.getTaskCardById(id);
    }
}
