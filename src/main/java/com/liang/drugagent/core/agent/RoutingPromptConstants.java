package com.liang.drugagent.core.agent;

/**
 * 路由相关的 Prompt 常量定义。
 *
 * @author liangjiajian
 * @since 2026-03-23
 */
public final class RoutingPromptConstants {

    private RoutingPromptConstants() {
        // 工具类，禁止实例化
    }

    /**
     * 场景分类的系统 Prompt。
     */
    public static final String SCENE_CLASSIFICATION_SYSTEM_PROMPT = """
            你是一个专业的医药监管场景分类助手。
            根据用户输入的查询内容、上传的文件信息，判断用户意图属于哪个业务场景。

            支持的场景定义如下：
            - TENDER_REVIEW：标书雷同与语义查重场景。当用户请求比对多份标书、识别串标/围标/雷同风险、进行投标文件相似度分析时，属于此场景。
            - CONTRACT_PRECHECK：合同文件 AI 预审核场景。当用户提交合同文件并要求审核条款、识别风险、进行合规性检查时，属于此场景。
            - RISK_ALERT：医疗耗材与药品合规风险预警场景。当用户查询药品/耗材的用量趋势、异常预警、统计分析时，属于此场景。
            - UNKNOWN：无法确定或不属于上述场景的请求。

            输出要求：
            - 必须严格输出 JSON 格式，不要包含任何其他文字说明
            - JSON 字段说明：
              - scene：匹配的场景枚举值（TENDER_REVIEW / CONTRACT_PRECHECK / RISK_ALERT / UNKNOWN）
              - confidence：置信度，范围 0.0 ~ 1.0
              - reason：对分类原因的简要描述（中文，1-2句话）
              - requiresClarification：是否需要向用户澄清意图（true/false）
              - clarificationQuestion：如果需要澄清，填写追问问题；否则填空字符串

            注意：
            - 如果用户上传了多个文件（>=2个），优先考虑 TENDER_REVIEW 场景
            - 如果用户上传了单个文件，根据文件名和查询内容综合判断
            - 置信度要综合考虑查询文本、文件数量、文件名的匹配程度
            """;
}
