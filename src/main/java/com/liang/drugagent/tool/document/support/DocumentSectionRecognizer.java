package com.liang.drugagent.tool.document.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文档章节识别器（已废弃）。
 *
 * <p><strong>已废弃：</strong>章节识别能力在当前 MVP 阶段不需要。
 * 此类仅作为消除废弃类编译错误而保留。</p>
 *
 * @deprecated 当前 MVP 阶段不做章节识别，后续需要时再实现
 */
@Slf4j
@Component
@Deprecated
public class DocumentSectionRecognizer {

    /**
     * 识别文本中的章节结构。
     *
     * @param lines 文本行列表
     * @return 章节节点列表
     */
    public List<SectionNode> recognize(List<String> lines) {
        log.debug("[DocumentSectionRecognizer] 章节识别当前为空实现");
        return List.of();
    }

    /**
     * 判断给定文本行是否为章节标题。
     *
     * @param line 文本行
     * @return 是否为章节标题
     */
    public boolean isSectionHeader(String line) {
        return false;
    }

    /**
     * 章节节点。
     */
    public record SectionNode(String title, int level, int startLine, int endLine) {
    }
}
