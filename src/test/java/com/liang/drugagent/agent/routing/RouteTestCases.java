package com.liang.drugagent.agent.routing;

import com.liang.drugagent.domain.req.DrugAgentReq;
import com.liang.drugagent.enums.SceneEnum;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 路由测试样例集。
 *
 * <p>包含 40 条测试样例，覆盖：</p>
 * <ul>
 *   <li>标书审查场景 (TENDER_REVIEW)</li>
 *   <li>合同预审场景 (CONTRACT_PRECHECK)</li>
 *   <li>风险预警场景 (RISK_ALERT)</li>
 *   <li>未知场景 (UNKNOWN)</li>
 *   <li>模糊表达</li>
 *   <li>多意图</li>
 *   <li>口语化表达</li>
 * </ul>
 *
 * @author liangjiajian
 */
@Getter
public class RouteTestCases {

    /**
     * 测试样例
     */
    @Builder
    public static class TestCase {
        /**
         * 场景描述
         */
        private String description;
        /**
         * 用户查询
         */
        private String query;
        /**
         * 期望的场景
         */
        private SceneEnum expectedScene;
        /**
         * 期望的决策来源
         */
        private String expectedSource;
        /**
         * 附件文件ID列表
         */
        private List<String> fileIds;
        /**
         * 场景提示
         */
        private String sceneHint;
        /**
         * 元数据
         */
        private java.util.Map<String, Object> metadata;
    }

    /**
     * 获取所有测试样例
     */
    public static List<TestCase> getAllCases() {
        return List.of(
                // ========== 标书审查场景 ==========
                // 文件相关
                createCase("多文件上传标书查重", "帮我看这两份投标文件有没有大段雷同",
                        SceneEnum.TENDER_REVIEW, "rule", List.of("file1.pdf", "file2.pdf")),
                createCase("单文件标书审查", "审查这份标书有没有问题",
                        SceneEnum.TENDER_REVIEW, "rule", List.of("tender.pdf")),
                createCase("多文件上传", "比较一下这几个投标单位的文件",
                        SceneEnum.TENDER_REVIEW, "rule", List.of("bid1.doc", "bid2.doc", "bid3.doc")),

                // 文本关键词
                createCase("文本提及标书", "帮我看这份标书有没有串标风险",
                        SceneEnum.TENDER_REVIEW, "rule"),
                createCase("文本提及查重", "这几份投标文件需要查重分析",
                        SceneEnum.TENDER_REVIEW, "rule"),
                createCase("文本提及围标", "分析一下是否存在围标情况",
                        SceneEnum.TENDER_REVIEW, "rule"),
                createCase("文本提及雷同", "检查投标文件是否有雷同段落",
                        SceneEnum.TENDER_REVIEW, "rule"),
                createCase("文本提及投标", "我参与了投标，想审查一下文件合规性",
                        SceneEnum.TENDER_REVIEW, "rule"),

                // 复杂场景
                createCase("多文件语义查重", "上传了三份标书，帮我检测一下有没有语义高度相似的内容",
                        SceneEnum.TENDER_REVIEW, "rule", List.of("t1.pdf", "t2.pdf", "t3.pdf")),

                // ========== 合同预审场景 ==========
                // 文件相关
                createCase("单文件合同审查", "审查这份供应协议里有没有不利条款",
                        SceneEnum.CONTRACT_PRECHECK, "rule", List.of("contract.pdf")),
                createCase("合同文件上传", "帮我看看这个合同文本有没有风险",
                        SceneEnum.CONTRACT_PRECHECK, "rule", List.of("agreement.docx")),

                // 文本关键词
                createCase("文本提及合同", "这份合同需要法务审核",
                        SceneEnum.CONTRACT_PRECHECK, "rule"),
                createCase("文本提及协议", "分析一下供应协议里的条款",
                        SceneEnum.CONTRACT_PRECHECK, "rule"),
                createCase("文本提及条款", "合同里有哪些条款对我方不利",
                        SceneEnum.CONTRACT_PRECHECK, "rule"),
                createCase("文本提及法务", "需要法务审查这份文件",
                        SceneEnum.CONTRACT_PRECHECK, "rule"),
                createCase("文本提及审核", "帮我审核一下采购合同",
                        SceneEnum.CONTRACT_PRECHECK, "rule"),
                createCase("文本提及预审", "合同预审，排查风险点",
                        SceneEnum.CONTRACT_PRECHECK, "rule"),
                createCase("文本提及审查", "审查合作协议的有效性",
                        SceneEnum.CONTRACT_PRECHECK, "rule"),

                // ========== 风险预警场景 ==========
                // 文本关键词
                createCase("药品关键词", "分析近三个月药品采购异常波动",
                        SceneEnum.RISK_ALERT, "rule"),
                createCase("耗材关键词", "耗材用量出现异常增长，什么原因",
                        SceneEnum.RISK_ALERT, "rule"),
                createCase("预警关键词", "近期有哪些合规风险需要关注",
                        SceneEnum.RISK_ALERT, "rule"),
                createCase("异常关键词", "检测药品使用异常情况",
                        SceneEnum.RISK_ALERT, "rule"),
                createCase("用量关键词", "分析最近一周的药品用量趋势",
                        SceneEnum.RISK_ALERT, "rule"),
                createCase("趋势关键词", "耗材采购有什么趋势变化",
                        SceneEnum.RISK_ALERT, "rule"),
                createCase("统计关键词", "统计一下本月药品使用情况",
                        SceneEnum.RISK_ALERT, "rule"),
                createCase("分析关键词", "分析这批医疗耗材的合规性",
                        SceneEnum.RISK_ALERT, "rule"),

                // ========== 模糊/口语化表达 ==========
                createCase("模糊表达-上传文件看风险", "我上传了一个文件，帮我看看有没有风险",
                        SceneEnum.UNKNOWN, "rule"),
                createCase("口语化-帮我看看", "帮我看看这个怎么样",
                        SceneEnum.UNKNOWN, "llm"),
                createCase("口语化-问一下", "想问一下关于采购的事",
                        SceneEnum.UNKNOWN, "llm"),
                createCase("模糊表达-这个行不行", "这个文件合规吗",
                        SceneEnum.UNKNOWN, "llm"),
                createCase("多意图-既要又要", "既要看合同风险，又要查标书相似度",
                        SceneEnum.UNKNOWN, "llm"),
                createCase("闲聊", "今天天气不错",
                        SceneEnum.UNKNOWN, "llm"),

                // ========== 前端指定场景 ==========
                createCase("显式指定标书场景", "帮我分析一下",
                        SceneEnum.TENDER_REVIEW, "sceneHint", null, "TENDER_REVIEW"),
                createCase("显式指定合同场景", "看看这个有没有问题",
                        SceneEnum.CONTRACT_PRECHECK, "sceneHint", null, "CONTRACT_PRECHECK"),
                createCase("显式指定风险场景", "有什么风险需要注意",
                        SceneEnum.RISK_ALERT, "sceneHint", null, "RISK_ALERT"),

                // ========== LLM 特有场景 ==========
                createCase("语义理解-复杂", "我们公司在参加一个药品集中采购的招标，想请AI帮我分析一下我们准备竞标的标书与其他竞争对手相比有哪些优势和不足",
                        SceneEnum.TENDER_REVIEW, "llm"),
                createCase("语义理解-风险导向", "最近医院采购了一批新型号的一次性注射器，想了解这批产品在临床使用中是否有潜在的安全隐患或者不良反应报告",
                        SceneEnum.RISK_ALERT, "llm"),
                createCase("语义理解-合同分析", "作为采购部门负责人，我想了解跟我们合作的供应商近期是否出现过产品召回或者质量抽检不合格的情况",
                        SceneEnum.RISK_ALERT, "llm"),

                // ========== 边界条件 ==========
                createCase("空查询", "",
                        SceneEnum.UNKNOWN, "fallback"),
                createCase("纯空格", "   ",
                        SceneEnum.UNKNOWN, "fallback"),
                createCase("无效场景提示", "随便看看",
                        SceneEnum.UNKNOWN, "llm", null, "INVALID_SCENE")
        );
    }

    /**
     * 创建测试用例
     */
    private static TestCase createCase(String description, String query, SceneEnum expectedScene,
                                        String expectedSource) {
        return TestCase.builder()
                .description(description)
                .query(query)
                .expectedScene(expectedScene)
                .expectedSource(expectedSource)
                .build();
    }

    /**
     * 创建带文件ID的测试用例
     */
    private static TestCase createCase(String description, String query, SceneEnum expectedScene,
                                        String expectedSource, List<String> fileIds) {
        return TestCase.builder()
                .description(description)
                .query(query)
                .expectedScene(expectedScene)
                .expectedSource(expectedSource)
                .fileIds(fileIds)
                .build();
    }

    /**
     * 创建带场景提示的测试用例
     */
    private static TestCase createCase(String description, String query, SceneEnum expectedScene,
                                        String expectedSource, List<String> fileIds, String sceneHint) {
        return TestCase.builder()
                .description(description)
                .query(query)
                .expectedScene(expectedScene)
                .expectedSource(expectedSource)
                .fileIds(fileIds)
                .sceneHint(sceneHint)
                .build();
    }

    /**
     * 将测试用例转换为 DrugAgentReq
     */
    public static DrugAgentReq toRequest(TestCase testCase) {
        return DrugAgentReq.builder()
                .query(testCase.getQuery())
                .fileIds(testCase.getFileIds())
                .sceneHint(testCase.getSceneHint())
                .metadata(testCase.getMetadata())
                .build();
    }
}
