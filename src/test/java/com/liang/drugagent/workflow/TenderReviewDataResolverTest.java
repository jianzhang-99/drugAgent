package com.liang.drugagent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.agent.AgentContext;
import com.liang.drugagent.domain.req.DrugAgentReq;
import com.liang.drugagent.domain.tenderreview.Field;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import com.liang.drugagent.service.tenderreview.TenderReviewDataResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenderReviewDataResolverTest {

    private final TenderReviewDataResolver resolver = new TenderReviewDataResolver(new ObjectMapper());

    @Test
    void shouldBuildTenderReviewDataFromMetadataDocuments() {
        DrugAgentReq req = new DrugAgentReq();
        req.setMetadata(Map.of(
                "caseId", "CASE-001",
                "documents", List.of(
                        Map.of(
                                "documentId", "DOC-A",
                                "documentName", "投标人A",
                                "fileType", "md",
                                "content", """
                                        # 投标文件：城市视频云平台升级项目

                                        ## 第一部分：投标人基本信息
                                        | 投标人名称 | 晟博云创工程有限公司 |
                                        | :--- | :--- |
                                        | 项目联系人 | 张工 |
                                        | 联系电话 | 139-1111-2222 |

                                        ## 第二部分：技术方案摘要
                                        1. 平滑迁移：采用双活架构，确保迁移期间业务不中断。
                                        2. 存储扩展：基于分布式存储架构，支持 PB 级扩容。
                                        3. 安全保障：全面适配三级等保要求。

                                        ## 第三部分：项目核心团队
                                        | 姓名 | 岗位 | 从业年限 | 个人履历摘要 |
                                        | :--- | :--- | :--- | :--- |
                                        | 周景 | 项目经理 | 11年 | 主持过市域视频平台、雪亮工程及边界监控平台建设。 |

                                        ## 第四部分：投标报价清单
                                        | 序号 | 分项名称 | 数量 | 单价 | 小计 |
                                        | :--- | :--- | :--- | :--- | :--- |
                                        | 1 | 平台迁移服务 | 1 | 100000 | 100000 |
                                        | 2 | 存储服务 | 1 | 200000 | 200000 |
                                        | 3 | 运维服务 | 1 | 300000 | 300000 |

                                        ## 第五部分：服务承诺
                                        一般问题 4 小时响应，重大问题 2 小时到场。

                                        ## 第六部分：项目风险识别表
                                        | 风险项 | 影响分析 | 应对措施 |
                                        | :--- | :--- | :--- |
                                        | 主数据口径不统一 | 报表口径差异 | 先完成数据字典冻结 |

                                        ## 第七部分：历史成功案例
                                        我方曾为某制造集团建设供应商协同平台，实现 8 家工厂、600 余家供应商在线协同。
                                        """
                        ),
                        Map.of(
                                "documentId", "DOC-B",
                                "documentName", "投标人B",
                                "fileType", "md",
                                "content", """
                                        # 投标文件：城市视频云平台升级项目

                                        ## 第一部分：投标人基本信息
                                        | 投标人名称 | 晟拓数科有限公司 |
                                        | :--- | :--- |
                                        | 项目联系人 | 张工 |
                                        | 联系电话 | 139-1111-2229 |

                                        ## 第二部分：技术方案摘要
                                        1. 平滑迁移：采用双活架构，确保迁移期间业务不中断。
                                        2. 存储扩展：基于分布式存储架构，支持 PB 级扩容。
                                        3. 安全保障：全面适配三级等保要求。

                                        ## 第三部分：项目核心团队
                                        | 姓名 | 岗位 | 从业年限 | 个人履历摘要 |
                                        | :--- | :--- | :--- | :--- |
                                        | 周景 | 项目经理 | 11年 | 主持过市域视频平台、雪亮工程及边界监控平台建设。 |

                                        ## 第四部分：投标报价清单
                                        | 序号 | 分项名称 | 数量 | 单价 | 小计 |
                                        | :--- | :--- | :--- | :--- | :--- |
                                        | 1 | 平台迁移服务 | 1 | 102000 | 102000 |
                                        | 2 | 存储服务 | 1 | 202000 | 202000 |
                                        | 3 | 运维服务 | 1 | 302000 | 302000 |

                                        ## 第五部分：服务承诺
                                        一般问题 4 小时响应，重大问题 2 小时到场。

                                        ## 第六部分：项目风险识别表
                                        | 风险项 | 影响分析 | 应对措施 |
                                        | :--- | :--- | :--- |
                                        | 主数据口径不统一 | 报表口径差异 | 先完成数据字典冻结 |

                                        ## 第七部分：历史成功案例
                                        我方曾为某制造集团建设供应商协同平台，实现 8 家工厂、600 余家供应商在线协同。
                                        """
                        )
                )
        ));

        TenderReviewData data = resolver.resolve(AgentContext.from(req));

        assertNotNull(data);
        assertEquals("CASE-001", data.getCase().getCaseId());
        assertEquals(2, data.getDocuments().size());
        assertEquals(1, data.getCompareScopes().size());
        assertFalse(data.getBlocks().isEmpty());
        assertFalse(data.getFields().isEmpty());

        assertTrue(hasFieldType(data.getFields(), "contact_person"));
        assertTrue(hasFieldType(data.getFields(), "contact_phone"));
        assertTrue(hasFieldType(data.getFields(), "quote_item"));
        assertTrue(hasFieldType(data.getFields(), "team_member"));
        assertTrue(hasFieldType(data.getFields(), "proposal_segment"));
        assertTrue(hasFieldType(data.getFields(), "service_commitment"));
        assertTrue(hasFieldType(data.getFields(), "risk_identification"));
        assertTrue(hasFieldType(data.getFields(), "case_data"));
    }

    private boolean hasFieldType(List<Field> fields, String fieldType) {
        return fields.stream().anyMatch(field -> fieldType.equals(field.getFieldType()));
    }
}
