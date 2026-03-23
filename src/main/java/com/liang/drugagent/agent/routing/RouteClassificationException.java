package com.liang.drugagent.agent.routing;

/**
 * 路由分类异常。
 *
 * <p>当百炼模型调用过程中发生不可恢复错误时抛出。</p>
 *
 * @author liangjiajian
 */
public class RouteClassificationException extends Exception {

    public RouteClassificationException(String message) {
        super(message);
    }

    public RouteClassificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
