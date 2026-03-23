package com.liang.drugagent.agent;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 意图理解上下文。
 *
 * <p>封装用于 LLM 意图理解的完整上下文信息，
 * 由 ContextEnricher 构建。</p>
 *
 * @author liangjiajian
 * @since 2026-03-23
 */
@Getter
@Builder
public class IntentUnderstandingContext {

    /**
     * 用户原始查询
     */
    private String query;

    /**
     * 用户上传的文件列表（文件名）
     */
    private List<String> uploadedFileNames;

    /**
     * 文件数量
     */
    private int fileCount;

    /**
     * 前端或调用方显式指定的场景提示
     */
    private String sceneHint;

    /**
     * 历史会话摘要（最近 N 条消息）
     */
    private String recentConversationSummary;

    /**
     * 场景候选列表
     */
    private List<String> availableScenes;

    /**
     * 规则信号（如果规则命中了某些特征）
     */
    private Map<String, Object> ruleSignals;

    /**
     * 是否包含附件
     */
    private boolean hasAttachments;

    /**
     * 构建意图理解上下文字符串（用于 prompt）
     */
    public String toPromptContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("【用户查询】\n").append(query != null ? query : "(无)").append("\n\n");

        if (hasAttachments) {
            sb.append("【上传文件】\n");
            if (uploadedFileNames != null && !uploadedFileNames.isEmpty()) {
                for (int i = 0; i < uploadedFileNames.size(); i++) {
                    sb.append(String.format("  %d. %s\n", i + 1, uploadedFileNames.get(i)));
                }
            } else {
                sb.append("  (文件信息不可用)\n");
            }
            sb.append(String.format("【文件数量】%d 份\n\n", fileCount));
        }

        if (sceneHint != null && !sceneHint.isBlank()) {
            sb.append("【指定场景】").append(sceneHint).append("\n\n");
        }

        if (recentConversationSummary != null && !recentConversationSummary.isBlank()) {
            sb.append("【历史对话摘要】\n").append(recentConversationSummary).append("\n\n");
        }

        sb.append("【可用场景】\n");
        if (availableScenes != null) {
            for (String scene : availableScenes) {
                sb.append("  - ").append(scene).append("\n");
            }
        }
        sb.append("\n");

        if (ruleSignals != null && !ruleSignals.isEmpty()) {
            sb.append("【规则信号】（仅供参考）\n");
            ruleSignals.forEach((key, value) -> {
                if (value != null) {
                    sb.append(String.format("  %s: %s\n", key, value));
                }
            });
            sb.append("\n");
        }

        return sb.toString();
    }
}
