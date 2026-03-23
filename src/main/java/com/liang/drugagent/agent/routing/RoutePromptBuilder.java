package com.liang.drugagent.agent.routing;

import com.liang.drugagent.agent.util.RoutingPromptConstants;
import com.liang.drugagent.enums.SceneEnum;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 路由分类 Prompt 构造器。
 *
 * <p>负责根据用户请求、文件信息和场景定义，
 * 构造发送给百炼模型的分类 prompt。</p>
 *
 * @author liangjiajian
 */
@Component
public class RoutePromptBuilder {

    private static final String SYSTEM_PROMPT = RoutingPromptConstants.SCENE_CLASSIFICATION_SYSTEM_PROMPT;

    /**
     * 构建发送给模型的完整 prompt。
     *
     * @param query       用户查询文本
     * @param fileIds     上传的文件 ID 列表
     * @param fileNames   上传的文件名列表
     * @return 构造好的 prompt
     */
    public String buildPrompt(String query, List<String> fileIds, List<String> fileNames) {
        StringBuilder userContent = new StringBuilder();
        userContent.append("用户查询：").append(query == null ? "（空）" : query).append("\n");
        userContent.append("上传文件数量：").append(fileIds == null ? 0 : fileIds.size()).append("\n");

        if (fileNames != null && !fileNames.isEmpty()) {
            userContent.append("上传文件名列表：\n");
            for (int i = 0; i < fileNames.size(); i++) {
                userContent.append("  ").append(i + 1).append(". ").append(fileNames.get(i)).append("\n");
            }
        } else {
            userContent.append("上传文件名列表：无\n");
        }

        userContent.append("\n请根据以上信息，判断用户意图所属的业务场景。");

        return SYSTEM_PROMPT + "\n\n" + userContent;
    }

    /**
     * 获取支持的所有场景枚举列表。
     *
     * @return 场景枚举列表
     */
    public static SceneEnum[] getSupportedScenes() {
        return SceneEnum.values();
    }
}
