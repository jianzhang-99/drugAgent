# 模块C：法规知识库（RAG）— 技术实现方案

> **版本**：v1.0 | **日期**：2026-03-02 | **技术栈**：Spring Boot 4.0 + DashScope Embedding API + MySQL 向量存储

---

## 一、模块概述

法规知识库模块是系统的 **RAG（Retrieval-Augmented Generation）** 引擎。通过将法规文件进行文本切片、向量化后存储，在合规对话时自动检索最相关的法规片段注入到 Prompt 中，让大模型基于真实法规回答，避免"幻觉"问题。

### 核心数据流

```
法规文件上传 → 文本提取 → 文本切片(Chunking) → Embedding向量化 → 存入数据库
                                                                      ↓
用户提问 → 问题Embedding → 余弦相似度匹配 → Top-K片段 → 注入Prompt → 千问回答
```

### RAG 原理图

```
┌───────────────────────────────────────────────────────┐
│                    离线入库阶段                         │
│                                                       │
│  法规PDF ──→ 文本提取 ──→ 文本切片 ──→ Embedding API  │
│                            ↓                  ↓       │
│                     [片段1][片段2]..   [向量1][向量2]..│
│                            ↓                  ↓       │
│                     knowledge_chunk 表 (content+embedding) │
└───────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────────┐
│                    在线检索阶段                         │
│                                                       │
│  用户问题 ──→ Embedding API ──→ 问题向量              │
│                                    ↓                  │
│                         余弦相似度计算(vs 所有chunk)    │
│                                    ↓                  │
│                         排序 → 取 Top-5 片段           │
│                                    ↓                  │
│                    注入到合规对话 Prompt 中             │
└───────────────────────────────────────────────────────┘
```

---

## 二、包结构设计

```
com.liang.drugagent
├── controller
│   └── KnowledgeController.java        // 知识库管理接口
├── service
│   ├── KnowledgeService.java           // 知识库入库/管理服务
│   ├── EmbeddingService.java           // 向量化封装
│   ├── TextSplitter.java               // 文本切片器
│   └── RAGEngine.java                  // 检索引擎
├── mapper
│   ├── KnowledgeFileMapper.java
│   └── KnowledgeChunkMapper.java
├── entity
│   ├── KnowledgeFile.java              // 知识库文件记录
│   └── KnowledgeChunk.java             // 知识库片段(含向量)
├── vo
│   ├── KnowledgeFileVO.java
│   └── SearchResultVO.java             // 检索结果
└── util
    └── VectorUtils.java                // 向量运算工具
```

---

## 三、数据库设计

### 3.1 `knowledge_file`（知识库文件表）

```sql
CREATE TABLE knowledge_file (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY  COMMENT '主键',
    file_name       VARCHAR(255)   NOT NULL             COMMENT '原始文件名',
    file_path       VARCHAR(500)   NOT NULL             COMMENT '存储路径',
    file_type       VARCHAR(20)    NOT NULL             COMMENT '文件类型',
    file_size       BIGINT         DEFAULT 0            COMMENT '文件大小',
    chunk_count     INT            DEFAULT 0            COMMENT '切片数量',
    status          TINYINT        DEFAULT 0            COMMENT '状态: 0处理中 1完成 2失败',
    error_msg       VARCHAR(500)   DEFAULT NULL          COMMENT '失败原因',
    created_at      DATETIME       DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文件';
```

### 3.2 `knowledge_chunk`（知识库片段表）

```sql
CREATE TABLE knowledge_chunk (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY  COMMENT '主键',
    file_id         BIGINT         NOT NULL             COMMENT '来源文件ID',
    chunk_index     INT            NOT NULL             COMMENT '片段序号(从0开始)',
    content         TEXT           NOT NULL             COMMENT '片段文本内容',
    -- 向量存储方案: 将float[]序列化为JSON字符串存储
    embedding       MEDIUMTEXT     DEFAULT NULL          COMMENT '向量(JSON数组格式)',
    content_length  INT            DEFAULT 0            COMMENT '文本字符数',
    source_desc     VARCHAR(255)   DEFAULT NULL          COMMENT '来源描述(如: 药品管理法-第3章)',
    created_at      DATETIME       DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_file_id (file_id),
    FOREIGN KEY (file_id) REFERENCES knowledge_file(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库向量片段';
```

> **向量存储方案选择**：
> - **方案A（本项目采用）**：将向量序列化为 JSON 字符串存 `MEDIUMTEXT`，检索时全表加载到 Java 内存计算。适合小规模知识库（< 10万条片段）。
> - **方案B（未来扩展）**：接入 Milvus / Elasticsearch KNN 等专业向量数据库。适合大规模场景。

---

## 四、功能详细实现

### 4.1 文本切片（Chunking）

#### 4.1.1 切片策略

```
原文（例如3000字的法规文档）：
════════════════════════════════════════════════
第一条 为了加强药品管理...
第二条 在中华人民共和国境内...
...
第一百五十五条 本法自2019年12月1日起施行
════════════════════════════════════════════════

切片参数:
  chunk_size    = 500字  （每个片段大约500字）
  chunk_overlap = 100字  （相邻片段有100字重叠，防止上下文断裂）

切片结果:
  片段0: [0    ~ 500]   ████████████
  片段1: [400  ~ 900]       ████████████
  片段2: [800  ~ 1300]          ████████████
  片段3: [1200 ~ 1700]              ████████████
  ...
```

#### 4.1.2 实现代码

```java
// TextSplitter.java
@Component
public class TextSplitter {
    
    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_OVERLAP = 100;
    
    /**
     * 将文本按段落感知方式切片
     * 优先在段落边界（换行符）处断开，避免在句子中间切断
     */
    public List<TextChunk> split(String text) {
        return split(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }
    
    public List<TextChunk> split(String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) return List.of();
        
        List<TextChunk> chunks = new ArrayList<>();
        int start = 0;
        int index = 0;
        
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            
            // 尝试在段落边界处断开（往前找最近的换行符）
            if (end < text.length()) {
                int newlinePos = text.lastIndexOf('\n', end);
                if (newlinePos > start + chunkSize / 2) {
                    end = newlinePos + 1;  // 在换行处切分
                }
            }
            
            String chunkText = text.substring(start, end).trim();
            if (!chunkText.isEmpty()) {
                chunks.add(new TextChunk(index++, chunkText));
            }
            
            // 下一个片段起始位置 = 当前结束位置 - 重叠量
            start = end - overlap;
            if (start >= text.length()) break;
        }
        
        return chunks;
    }
    
    @Data
    @AllArgsConstructor
    public static class TextChunk {
        private int index;
        private String content;
    }
}
```

---

### 4.2 Embedding 向量化

#### 4.2.1 千问 Embedding API 说明

```
API: DashScope text-embedding-v3
输入: 一段文本
输出: 1024维浮点数组 float[1024]
用途: 将文本语义编码为向量空间中的一个点
```

#### 4.2.2 实现代码

```java
// EmbeddingService.java
@Service
@Slf4j
public class EmbeddingService {

    private final QwenConfig qwenConfig;
    
    private static final String EMBEDDING_MODEL = "text-embedding-v3";
    
    /**
     * 单条文本向量化
     */
    public float[] embed(String text) {
        TextEmbedding embedding = new TextEmbedding();
        TextEmbeddingParam param = TextEmbeddingParam.builder()
                .apiKey(qwenConfig.getApiKey())
                .model(EMBEDDING_MODEL)
                .text(text)
                .build();
        try {
            TextEmbeddingResult result = embedding.call(param);
            List<Double> vector = result.getOutput()
                .getEmbeddings().get(0).getEmbedding();
            
            // Double[] → float[]
            float[] floatVector = new float[vector.size()];
            for (int i = 0; i < vector.size(); i++) {
                floatVector[i] = vector.get(i).floatValue();
            }
            return floatVector;
            
        } catch (Exception e) {
            log.error("Embedding调用失败, text长度={}", text.length(), e);
            throw new BusinessException("向量化服务异常");
        }
    }
    
    /**
     * 批量向量化（DashScope支持每次最多25条）
     */
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> results = new ArrayList<>();
        // 每25条为一批
        for (int i = 0; i < texts.size(); i += 25) {
            List<String> batch = texts.subList(i, Math.min(i + 25, texts.size()));
            TextEmbeddingParam param = TextEmbeddingParam.builder()
                    .apiKey(qwenConfig.getApiKey())
                    .model(EMBEDDING_MODEL)
                    .texts(batch)
                    .build();
            try {
                TextEmbeddingResult result = new TextEmbedding().call(param);
                for (var emb : result.getOutput().getEmbeddings()) {
                    float[] vec = new float[emb.getEmbedding().size()];
                    for (int j = 0; j < emb.getEmbedding().size(); j++) {
                        vec[j] = emb.getEmbedding().get(j).floatValue();
                    }
                    results.add(vec);
                }
            } catch (Exception e) {
                log.error("批量Embedding失败, batch size={}", batch.size(), e);
                throw new BusinessException("向量化服务异常");
            }
        }
        return results;
    }
}
```

---

### 4.3 入库流水线

```java
// KnowledgeService.java
@Service
@Slf4j
public class KnowledgeService {

    private final FileParserService fileParserService;
    private final TextSplitter textSplitter;
    private final EmbeddingService embeddingService;
    private final KnowledgeFileMapper fileMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final ObjectMapper objectMapper;
    
    /**
     * 法规文件入库流水线（异步执行）
     */
    @Async
    public void ingest(KnowledgeFile knowledgeFile) {
        try {
            log.info("开始处理知识库文件: {}", knowledgeFile.getFileName());
            
            // 1. 解析文本
            FileParser parser = fileParserService.getParser(knowledgeFile.getFileType());
            String fullText = parser.parse(Path.of(knowledgeFile.getFilePath()));
            log.info("文本提取完成, 长度={}", fullText.length());
            
            // 2. 切片
            List<TextSplitter.TextChunk> chunks = textSplitter.split(fullText);
            log.info("切片完成, 片段数={}", chunks.size());
            
            // 3. 批量向量化
            List<String> texts = chunks.stream()
                .map(TextSplitter.TextChunk::getContent)
                .toList();
            List<float[]> embeddings = embeddingService.embedBatch(texts);
            log.info("向量化完成");
            
            // 4. 批量入库
            List<KnowledgeChunk> entities = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                KnowledgeChunk entity = new KnowledgeChunk();
                entity.setFileId(knowledgeFile.getId());
                entity.setChunkIndex(i);
                entity.setContent(chunks.get(i).getContent());
                entity.setEmbedding(objectMapper.writeValueAsString(embeddings.get(i)));
                entity.setContentLength(chunks.get(i).getContent().length());
                entity.setSourceDesc(knowledgeFile.getFileName() + " - 片段" + (i + 1));
                entities.add(entity);
            }
            chunkMapper.insertBatchSomeColumn(entities);
            
            // 5. 更新文件状态
            knowledgeFile.setStatus(1);
            knowledgeFile.setChunkCount(chunks.size());
            fileMapper.updateById(knowledgeFile);
            
            log.info("知识库文件 {} 入库完成, 共{}个片段", 
                knowledgeFile.getFileName(), chunks.size());
                
        } catch (Exception e) {
            log.error("知识库文件处理失败: {}", knowledgeFile.getFileName(), e);
            knowledgeFile.setStatus(2);
            knowledgeFile.setErrorMsg(e.getMessage());
            fileMapper.updateById(knowledgeFile);
        }
    }
}
```

---

### 4.4 向量检索引擎（RAGEngine）

#### 4.4.1 余弦相似度

```java
// VectorUtils.java
public class VectorUtils {
    
    /**
     * 计算两个向量的余弦相似度
     * 返回值范围 [-1, 1]，越接近1越相似
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("向量维度不一致");
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return denominator == 0 ? 0 : dotProduct / denominator;
    }
}
```

#### 4.4.2 Top-K 检索

```java
// RAGEngine.java
@Service
@Slf4j
public class RAGEngine {

    private final EmbeddingService embeddingService;
    private final KnowledgeChunkMapper chunkMapper;
    private final ObjectMapper objectMapper;
    
    private static final double SIMILARITY_THRESHOLD = 0.3;  // 最低相似度阈值
    
    /**
     * 检索与问题最相关的 Top-K 法规片段
     */
    public List<String> search(String question, int topK) {
        // 1. 将问题向量化
        float[] questionVector = embeddingService.embed(question);
        
        // 2. 加载所有 chunk 的向量
        List<KnowledgeChunk> allChunks = chunkMapper.selectList(null);
        
        if (allChunks.isEmpty()) {
            log.info("知识库为空，跳过RAG检索");
            return Collections.emptyList();
        }
        
        // 3. 计算相似度并排序
        List<SearchResult> results = allChunks.stream()
            .map(chunk -> {
                try {
                    float[] chunkVector = parseVector(chunk.getEmbedding());
                    double similarity = VectorUtils.cosineSimilarity(questionVector, chunkVector);
                    return new SearchResult(chunk.getContent(), similarity, chunk.getSourceDesc());
                } catch (Exception e) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .filter(r -> r.similarity >= SIMILARITY_THRESHOLD)
            .sorted(Comparator.comparingDouble(SearchResult::similarity).reversed())
            .limit(topK)
            .toList();
        
        log.info("RAG检索完成: 问题='{}', 匹配{}条, Top1相似度={}", 
            question, results.size(), 
            results.isEmpty() ? 0 : String.format("%.3f", results.get(0).similarity));
        
        return results.stream()
            .map(SearchResult::content)
            .toList();
    }
    
    /**
     * 检索带分数的结果（用于检索效果测试）
     */
    public List<SearchResultVO> searchWithScore(String question, int topK) {
        float[] questionVector = embeddingService.embed(question);
        List<KnowledgeChunk> allChunks = chunkMapper.selectList(null);
        
        return allChunks.stream()
            .map(chunk -> {
                float[] chunkVector = parseVector(chunk.getEmbedding());
                double similarity = VectorUtils.cosineSimilarity(questionVector, chunkVector);
                return new SearchResultVO(
                    chunk.getContent(), 
                    similarity, 
                    chunk.getSourceDesc(),
                    chunk.getFileId()
                );
            })
            .filter(r -> r.getSimilarity() >= SIMILARITY_THRESHOLD)
            .sorted(Comparator.comparingDouble(SearchResultVO::getSimilarity).reversed())
            .limit(topK)
            .toList();
    }
    
    private float[] parseVector(String jsonVector) {
        try {
            float[] vec = objectMapper.readValue(jsonVector, float[].class);
            return vec;
        } catch (Exception e) {
            throw new RuntimeException("向量解析失败", e);
        }
    }
    
    record SearchResult(String content, double similarity, String source) {}
}
```

---

### 4.5 知识库管理接口

```
# 上传法规文件并入库
POST /api/knowledge/upload
Content-Type: multipart/form-data
→ { fileId: 10, fileName: "药品管理法.pdf", status: 0, message: "正在处理中..." }

# 查询知识库文件列表
GET /api/knowledge/list
→ [{ id: 10, fileName: "药品管理法.pdf", chunkCount: 45, status: 1, createdAt: "..." }, ...]

# 删除知识库文件（级联删除所有chunk）
DELETE /api/knowledge/{id}
→ { message: "删除成功" }

# 检索效果测试
POST /api/knowledge/test-search
{ "query": "药品储存温度要求", "topK": 5 }
→ {
    "results": [
      { 
        "content": "第五十三条 药品经营企业应当按照药品的...", 
        "similarity": 0.87, 
        "source": "药品管理法.pdf - 片段28" 
      },
      ...
    ]
  }
```

---

## 五、性能优化策略

| 优化方向 | 当前方案 | 未来方案 |
|---------|---------|---------|
| 向量存储 | MySQL MEDIUMTEXT (JSON) | Milvus / pgvector 专业向量库 |
| 检索计算 | 全表加载 + Java内存计算 | 向量索引（HNSW / IVF） |
| 向量缓存 | 无 | 启动时预加载到内存 / Redis缓存 |
| 批量Embedding | 25条/批 | 异步队列 + 并发调用 |
| 大文件处理 | 异步 @Async | 消息队列（RabbitMQ） |

> **当前规模预估**：假设 50 个法规文件，每个文件切为 50 个片段 = 2500 条 chunk。全表加载 + 内存计算耗时 < 100ms，完全可以接受。

---

## 六、异常场景处理

| 场景 | 处理方式 |
|------|---------|
| Embedding API 调用失败 | 重试 3 次，仍失败则标记 status=2 |
| 法规文件过大（>100页） | 限制切片上限（如200个片段），超出部分警告 |
| 向量维度不一致 | 校验维度匹配，不一致时跳过 |
| 知识库为空时对话 | 跳过 RAG 检索步骤，仅基于文件内容对话 |
| 删除文件时有关联对话 | 仅删除知识库记录，对话历史保留 |
