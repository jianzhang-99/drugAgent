package com.liang.drugagent.shared.model.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 处置建议（报告第5页）。
 *
 * <p>按优先级分层展示处置建议，包括标题和具体行动列表。</p>
 *
 * @author liangjiajian
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Page5ActionSuggestions {

    /** 第一层级建议 */
    private ActionLevel level1;

    /** 第二层级建议 */
    private ActionLevel level2;

    /** 第三层级建议 */
    private ActionLevel level3;

    /** 留痕建议 */
    private List<String> retentionAdvice;

    /**
     * 行动层级。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionLevel {
        /** 标题 */
        private String title;
        /** 动作目标 */
        private String objective;
        /** 行动列表 */
        private List<Action> actions;
    }

    /**
     * 具体行动项。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Action {
        /** 行动内容 */
        private String action;
        /** 负责角色 */
        private String role;
        /** 优先级：高/中/低 */
        private String priority;
    }
}
