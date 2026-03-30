package com.liang.drugagent.tool.document;

import com.liang.drugagent.tool.document.support.DocumentTextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用文档解析入口。
 *
 * <p>职责：
 * <ul>
 *   <li>校验请求参数</li>
 *   <li>遍历多个临时文件</li>
 *   <li>根据扩展名选择对应 DocumentParser</li>
 *   <li>调用 DocumentTextNormalizer 做基础清洗</li>
 *   <li>汇总返回 DocumentToolResult</li>
 * </ul>
 *
 * <p>不负责：文件存储、文件权限管理、场景路由、风险判断。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentTool {

    private final List<DocumentParser> parsers;
    private final DocumentTextNormalizer normalizer;

    /**
     * 解析文档请求。
     *
     * @param req 文档工具请求
     * @return 统一解析结果
     */
    public DocumentToolResult parse(DocumentToolReq req) {
        if (req == null || req.getDocuments() == null || req.getDocuments().isEmpty()) {
            log.info("[DocumentTool] 请求文档为空");
            return DocumentToolResult.empty();
        }

        List<ParsedDocument> successDocs = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (TempDocument doc : req.getDocuments()) {
            log.info("[DocumentTool] 开始解析: documentId={}, filename={}",
                    doc.getDocumentId(), doc.getFilename());

            ParsedDocument parsed = parseSingle(doc);

            if (parsed.getPlainText() == null || parsed.getPlainText().isBlank()) {
                errors.add("文件解析失败或内容为空: " + doc.getFilename());
                continue;
            }

            // 调用 normalizer 清洗文本
            String normalized = normalizer.normalize(parsed.getPlainText());
            parsed.setNormalizedText(normalized);

            successDocs.add(parsed);
            log.info("[DocumentTool] 解析成功: documentId={}, textLength={}",
                    doc.getDocumentId(), normalized.length());
        }

        // 统计未解析的文件
        int failureCount = req.getDocuments().size() - successDocs.size();

        DocumentToolResult result = DocumentToolResult.builder()
                .documents(successDocs)
                .warnings(warnings)
                .errors(errors)
                .successCount(successDocs.size())
                .failureCount(failureCount)
                .build();

        log.info("[DocumentTool] 解析完成: success={}, failure={}",
                result.getSuccessCount(), result.getFailureCount());

        return result;
    }

    private ParsedDocument parseSingle(TempDocument doc) {
        for (DocumentParser parser : parsers) {
            if (parser.supports(doc.getFilename())) {
                try {
                    return parser.parse(doc);
                } catch (Exception e) {
                    log.error("[DocumentTool] 解析异常: documentId={}, parser={}, error={}",
                            doc.getDocumentId(), parser.getClass().getSimpleName(), e.getMessage());
                    return buildError(doc, "解析异常: " + e.getMessage());
                }
            }
        }

        log.warn("[DocumentTool] 无支持的解析器: filename={}", doc.getFilename());
        return buildError(doc, "不支持的文件格式: " + getExtension(doc.getFilename()));
    }

    private ParsedDocument buildError(TempDocument doc, String errorMsg) {
        return ParsedDocument.builder()
                .documentId(doc.getDocumentId())
                .filename(doc.getFilename())
                .fileType(getExtension(doc.getFilename()))
                .plainText("")
                .normalizedText("")
                .build();
    }

    private String getExtension(String filename) {
        if (filename == null) {
            return "unknown";
        }
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1).toLowerCase() : "unknown";
    }
}
