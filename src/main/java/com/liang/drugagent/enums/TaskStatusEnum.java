package com.liang.drugagent.enums;

/**
 * 任务状态枚举
 * 统一管理系统中所有任务的状态流转
 *
 * @author drug-agent-team
 */
public enum TaskStatusEnum {

    /**
     * 待处理 - 任务已创建，等待开始
     */
    PENDING("PENDING", "待处理", 0),

    /**
     * 解析中 - 文档正在解析
     */
    PARSING("PARSING", "解析中", 1),

    /**
     * 已解析 - 文档解析完成
     */
    PARSED("PARSED", "已解析", 2),

    /**
     * 执行中 - 任务正在执行
     */
    RUNNING("RUNNING", "执行中", 3),

    /**
     * 已完成 - 任务执行完成
     */
    COMPLETED("COMPLETED", "已完成", 4),

    /**
     * 已失败 - 任务执行失败
     */
    FAILED("FAILED", "已失败", 5),

    /**
     * 已取消 - 任务被取消
     */
    CANCELLED("CANCELLED", "已取消", 6);

    private final String code;
    private final String description;
    private final int order;

    TaskStatusEnum(String code, String description, int order) {
        this.code = code;
        this.description = description;
        this.order = order;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public int getOrder() {
        return order;
    }

    /**
     * 根据code获取枚举
     */
    public static TaskStatusEnum fromCode(String code) {
        for (TaskStatusEnum status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return PENDING;
    }

    /**
     * 判断是否为终态
     */
    public boolean isFinalState() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    /**
     * 判断是否为活跃状态
     */
    public boolean isActive() {
        return this == PENDING || this == PARSING || this == PARSED || this == RUNNING;
    }
}
