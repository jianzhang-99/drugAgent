package com.liang.drugagent.shared.vector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * VectorStore 包装器。
 *
 * <p>为 SimpleVectorStore 提供扩展能力，包括：
 * - 按 sourceId 删除文档（通过反射访问内部状态）
 * - 批量操作优化</p>
 */
@Slf4j
public class VectorStoreWrapper implements VectorStore {

    private final VectorStore delegate;

    public VectorStoreWrapper(VectorStore delegate) {
        this.delegate = delegate;
    }

    /**
     * 根据 sourceId 删除向量库中该文档的所有 chunks。
     *
     * <p>通过反射读取 SimpleVectorStore 内部状态实现。
     * 注意：这是兼容性方案，后续迁移到 PGVector 后将使用官方 API。</p>
     */
    public void deleteBySourceId(String sourceId) {
        if (!(delegate instanceof SimpleVectorStore simpleStore)) {
            log.warn("deleteBySourceId 仅支持 SimpleVectorStore，当前类型: {}", delegate.getClass().getName());
            return;
        }

        List<Document> allDocs = getDocumentsFromSimpleVectorStore(simpleStore);
        List<String> idsToRemove = allDocs.stream()
                .filter(doc -> {
                    Object sid = doc.getMetadata().get("sourceId");
                    return sid != null && sid.toString().equals(sourceId);
                })
                .map(Document::getId)
                .toList();

        if (!idsToRemove.isEmpty()) {
            simpleStore.delete(idsToRemove);
            log.info("向量库删除完成 - sourceId={}, 删除chunk数={}", sourceId, idsToRemove.size());
        } else {
            log.warn("向量库删除跳过：未找到匹配的 chunks - sourceId={}", sourceId);
        }
    }

    @Override
    public void add(List<Document> documents) {
        delegate.add(documents);
    }

    @Override
    public void delete(List<String> ids) {
        delegate.delete(ids);
    }

    @Override
    public void delete(Filter.Expression expression) {
        // 委托给VectorStore处理
        delegate.delete(expression);
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        return delegate.similaritySearch(request);
    }

    /**
     * 通过反射从 SimpleVectorStore 内部 map 中读取所有文档。
     *
     * <p>Spring AI 1.1.3 移除了 SimpleVectorStore.get() 公开方法，
     * 但文档仍存储在名为 documents 的 HashMap 字段中，
     * 通过反射读取以兼容当前版本。</p>
     */
    private List<Document> getDocumentsFromSimpleVectorStore(SimpleVectorStore simpleStore) {
        try {
            Field documentsField = SimpleVectorStore.class.getDeclaredField("documents");
            documentsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Document> docMap = (Map<String, Document>) documentsField.get(simpleStore);
            return List.copyOf(docMap.values());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.error("通过反射读取 SimpleVectorStore 内部 documents 失败", e);
            return List.of();
        }
    }
}
