# 模块B：文件合规对话 — 技术实现方案

> **版本**：v1.0 | **日期**：2026-03-02 | **技术栈**：Spring Boot 4.0 + PDFBox + Apache POI + 千问(DashScope)

---

## 一、模块概述

文件合规对话模块支持用户上传药品相关文件（PDF/Word/Excel），系统自动解析提取文本内容后，用户可基于文件内容与 AI 进行多轮合规审查对话。AI 将结合文件内容和法规知识库（RAG）给出合规判断。

### 核心数据流

```
文件上传 → 格式识别 → 文本提取 → 存入数据库
                                     ↓
用户提问 → 加载文件内容 → RAG检索法规 → 组装Prompt → 千问回答 → 保存消息
```

---

## 二、包结构设计

```
com.liang.drugagent
├── controller
│   ├── ComplianceController.java       // 合规对话 + 文件管理接口
│   └── SessionController.java          // 会话管理接口
├── service
│   ├── FileUploadService.java          // 文件上传通用服务
│   ├── FileParserService.java          // 文件解析引擎
│   ├── ComplianceService.java          // 合规对话核心服务
│   └── ChatMessageService.java         // 消息持久化服务
├── parser                              // 解析器策略
│   ├── FileParser.java                 // 解析器接口
│   ├── PdfParser.java                  // PDF 解析
│   ├── WordParser.java                 // Word 解析
│   └── ExcelTextParser.java            // Excel→文本 解析
├── mapper
│   ├── ComplianceFileMapper.java
│   ├── ChatSessionMapper.java
│   └── ChatMessageMapper.java
├── entity
│   ├── ComplianceFile.java
│   ├── ChatSession.java
│   └── ChatMessage.java
├── dto
│   └── ComplianceChatDTO.java          // 对话请求DTO
├── vo
│   ├── ChatMessageVO.java
│   └── SessionListVO.java
└── prompt
    └── CompliancePrompt.java           // 合规对话Prompt模板
```

---

## 三、数据库设计

### 3.1 `compliance_file`（合规文件表）

```sql
CREATE TABLE compliance_file (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY  COMMENT '主键',
    file_name       VARCHAR(255)   NOT NULL             COMMENT '原始文件名',
    file_path       VARCHAR(500)   NOT NULL             COMMENT '服务端存储路径',
    file_type       VARCHAR(20)    NOT NULL             COMMENT '文件类型(pdf/xlsx/docx)',
    file_size       BIGINT         DEFAULT 0            COMMENT '文件大小(字节)',
    parsed_content  LONGTEXT       DEFAULT NULL          COMMENT '解析后的纯文本',
    parse_status    TINYINT        DEFAULT 0            COMMENT '解析状态: 0待解析 1成功 2失败',
    parse_error     VARCHAR(500)   DEFAULT NULL          COMMENT '解析失败原因',
    created_at      DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    
    INDEX idx_created_at (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合规审查文件';
```

### 3.2 `chat_session`（会话表）

```sql
CREATE TABLE chat_session (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY  COMMENT '主键',
    session_type    VARCHAR(20)    NOT NULL             COMMENT '会话类型: compliance/monitor',
    file_id         BIGINT         DEFAULT NULL          COMMENT '关联文件ID(合规对话)',
    title           VARCHAR(200)   DEFAULT '新对话'      COMMENT '会话标题',
    created_at      DATETIME       DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_session_type (session_type),
    INDEX idx_updated_at (updated_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话会话';
```

### 3.3 `chat_message`（消息表）

```sql
CREATE TABLE chat_message (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY  COMMENT '主键',
    session_id      BIGINT         NOT NULL             COMMENT '所属会话ID',
    role            VARCHAR(20)    NOT NULL             COMMENT '角色: user/assistant/system',
    content         LONGTEXT       NOT NULL             COMMENT '消息内容',
    created_at      DATETIME       DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_session_id (session_id),
    FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话消息';
```

---

## 四、功能详细实现

### 4.1 文件上传（B1）

#### 4.1.1 上传流程

```
1. 校验文件格式（后缀 + MIME类型）
2. 生成唯一文件名（UUID + 原始后缀）防止重名
3. 保存到 uploads/compliance/ 目录
4. 写入 compliance_file 表（parse_status=0）
5. 异步触发文件解析
6. 返回文件ID + 基本信息
```

#### 4.1.2 接口设计

```
POST /api/compliance/file/upload
Content-Type: multipart/form-data

请求参数:
  file: 文件 (pdf/docx/xlsx)

响应:
{
  "code": 200,
  "data": {
    "fileId": 1,
    "fileName": "采购记录2026Q1.pdf",
    "fileType": "pdf",
    "fileSize": 1048576,
    "parseStatus": 0
  }
}
```

#### 4.1.3 核心实现

```java
// FileUploadService.java
@Service
public class FileUploadService {
    
    @Value("${drug.upload.path:uploads/}")
    private String uploadBasePath;
    
    private static final Set<String> ALLOWED_TYPES = 
        Set.of("pdf", "xlsx", "xls", "docx", "doc", "png", "jpg");
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
    
    public ComplianceFile uploadFile(MultipartFile file) {
        // 1. 校验
        String originalName = file.getOriginalFilename();
        String ext = getFileExtension(originalName).toLowerCase();
        if (!ALLOWED_TYPES.contains(ext)) {
            throw new BusinessException("不支持的文件格式: " + ext);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("文件大小超过50MB限制");
        }
        
        // 2. 保存文件
        String storedName = UUID.randomUUID() + "." + ext;
        Path targetPath = Paths.get(uploadBasePath, "compliance", storedName);
        Files.createDirectories(targetPath.getParent());
        file.transferTo(targetPath.toFile());
        
        // 3. 入库
        ComplianceFile record = new ComplianceFile();
        record.setFileName(originalName);
        record.setFilePath(targetPath.toString());
        record.setFileType(ext);
        record.setFileSize(file.getSize());
        record.setParseStatus(0);
        complianceFileMapper.insert(record);
        
        // 4. 异步解析
        fileParserService.parseAsync(record);
        
        return record;
    }
}
```

---

### 4.2 文件自动解析（B2）

#### 4.2.1 解析器策略模式

```java
// FileParser.java — 解析器接口
public interface FileParser {
    /** 是否支持该文件类型 */
    boolean supports(String fileType);
    
    /** 解析文件，返回纯文本 */
    String parse(Path filePath) throws Exception;
}
```

#### 4.2.2 PDF 解析

```java
// PdfParser.java
@Component
public class PdfParser implements FileParser {
    
    @Override
    public boolean supports(String fileType) {
        return "pdf".equalsIgnoreCase(fileType);
    }
    
    @Override
    public String parse(Path filePath) throws Exception {
        try (PDDocument doc = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);   // 按位置排序，更符合阅读顺序
            String text = stripper.getText(doc);
            
            // 清理：去除过多空白行
            return text.replaceAll("\\n{3,}", "\n\n").trim();
        }
    }
}
```

#### 4.2.3 Word 解析

```java
// WordParser.java
@Component
public class WordParser implements FileParser {
    
    @Override
    public boolean supports(String fileType) {
        return "docx".equalsIgnoreCase(fileType) || "doc".equalsIgnoreCase(fileType);
    }
    
    @Override
    public String parse(Path filePath) throws Exception {
        try (FileInputStream fis = new FileInputStream(filePath.toFile());
             XWPFDocument doc = new XWPFDocument(fis)) {
            
            StringBuilder sb = new StringBuilder();
            // 提取段落
            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText();
                if (StringUtils.hasText(text)) {
                    sb.append(text).append("\n");
                }
            }
            // 提取表格
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    List<String> cells = row.getTableCells().stream()
                        .map(XWPFTableCell::getText)
                        .toList();
                    sb.append(String.join(" | ", cells)).append("\n");
                }
                sb.append("\n");
            }
            return sb.toString().trim();
        }
    }
}
```

#### 4.2.4 Excel→文本 解析

```java
// ExcelTextParser.java
@Component
public class ExcelTextParser implements FileParser {
    
    @Override
    public boolean supports(String fileType) {
        return "xlsx".equalsIgnoreCase(fileType) || "xls".equalsIgnoreCase(fileType);
    }
    
    @Override
    public String parse(Path filePath) throws Exception {
        // 将Excel内容转为可读的描述性文本
        StringBuilder sb = new StringBuilder();
        try (Workbook wb = WorkbookFactory.create(filePath.toFile())) {
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                sb.append("【工作表：").append(sheet.getSheetName()).append("】\n");
                
                Row headerRow = sheet.getRow(0);
                if (headerRow == null) continue;
                
                // 读取表头
                List<String> headers = new ArrayList<>();
                for (Cell cell : headerRow) {
                    headers.add(getCellValue(cell));
                }
                
                // 逐行转为 "列名:值" 格式
                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    sb.append("第").append(r).append("行：");
                    for (int c = 0; c < headers.size(); c++) {
                        Cell cell = row.getCell(c);
                        sb.append(headers.get(c)).append("=")
                          .append(cell != null ? getCellValue(cell) : "空").append("；");
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
        }
        return sb.toString().trim();
    }
}
```

#### 4.2.5 解析调度器

```java
// FileParserService.java
@Service
public class FileParserService {

    private final List<FileParser> parsers;   // Spring自动注入所有实现
    
    @Async    // 异步解析，不阻塞上传接口
    public void parseAsync(ComplianceFile file) {
        try {
            FileParser parser = parsers.stream()
                .filter(p -> p.supports(file.getFileType()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("暂不支持该文件类型"));
            
            String text = parser.parse(Path.of(file.getFilePath()));
            
            // 限制文本长度，防止超长内容
            if (text.length() > 100_000) {
                text = text.substring(0, 100_000) + "\n...(内容过长，已截断)";
            }
            
            file.setParsedContent(text);
            file.setParseStatus(1);
            complianceFileMapper.updateById(file);
            
        } catch (Exception e) {
            file.setParseStatus(2);
            file.setParseError(e.getMessage());
            complianceFileMapper.updateById(file);
        }
    }
}
```

---

### 4.3 合规对话（B3 — 核心）

#### 4.3.1 对话流程

```
用户发送: { sessionId: 1, fileId: 5, message: "这个采购流程是否合规？" }
       │
       ▼
① 加载文件解析内容 (compliance_file.parsed_content)
       │
       ▼
② 从 RAG 知识库检索相关法规条款 (Top-5)      ← 模块C提供
       │
       ▼
③ 加载本会话历史消息 (最近10轮)
       │
       ▼
④ 组装消息列表:
   messages = [
     { role: "system", content: COMPLIANCE_SYSTEM_PROMPT },
     { role: "user",   content: "【历史对话摘要】..." },     // 如有
     { role: "user",   content: "【参考文件内容】..." },
     { role: "user",   content: "【相关法规】..." },          // RAG检索
     { role: "user",   content: "用户实际问题" }
   ]
       │
       ▼
⑤ 调用千问模型 → 获取回复
       │
       ▼
⑥ 保存 user消息 + assistant消息 到 chat_message 表
       │
       ▼
⑦ 返回 AI 回复给前端
```

#### 4.3.2 Prompt 模板

```java
// CompliancePrompt.java
public class CompliancePrompt {

    public static final String SYSTEM_PROMPT = """
        你是一位专业的药品合规审查专家，具有丰富的《药品管理法》、《药品经营质量管理规范》(GSP)
        及相关法规的知识。
        
        你的职责：
        1. 基于用户上传的文件内容，回答合规相关问题
        2. 引用具体的法规条款作为判断依据
        3. 如果发现不合规项，明确指出问题所在并给出改进建议
        4. 你的回答必须严谨、专业，不要编造不存在的法规条款
        5. 如果提供了参考法规内容，优先引用这些内容作为依据
        
        回答格式建议：
        - 合规判断：✅合规 / ⚠️部分合规 / ❌不合规
        - 详细分析
        - 法规依据
        - 改进建议（如需要）
        """;
    
    /** 构建文件内容上下文（截取关键片段，防止超Token） */
    public static String buildFileContext(String parsedContent) {
        if (parsedContent == null) return "";
        
        // 截取前8000字符（约4000 Token），确保不超限
        String truncated = parsedContent.length() > 8000 
            ? parsedContent.substring(0, 8000) + "\n...(内容已截取关键部分)" 
            : parsedContent;
        
        return "【参考文件内容】\n" + truncated;
    }
    
    /** 构建 RAG 法规检索上下文 */
    public static String buildRagContext(List<String> ragChunks) {
        if (ragChunks == null || ragChunks.isEmpty()) return "";
        
        StringBuilder sb = new StringBuilder("【相关法规条款参考】\n");
        for (int i = 0; i < ragChunks.size(); i++) {
            sb.append("法规片段").append(i + 1).append("：\n");
            sb.append(ragChunks.get(i)).append("\n\n");
        }
        return sb.toString();
    }
}
```

#### 4.3.3 合规对话 Service

```java
// ComplianceService.java
@Service
public class ComplianceService {

    private final QwenService qwenService;
    private final ComplianceFileMapper fileMapper;
    private final ChatMessageService chatMessageService;
    private final RAGEngine ragEngine;          // 模块C提供，暂可为空实现
    
    private static final int MAX_HISTORY_ROUNDS = 10;  // 最多保留10轮历史
    
    public String chat(ComplianceChatDTO dto) {
        // 1. 加载文件内容
        String fileContext = "";
        if (dto.getFileId() != null) {
            ComplianceFile file = fileMapper.selectById(dto.getFileId());
            if (file != null && file.getParseStatus() == 1) {
                fileContext = CompliancePrompt.buildFileContext(file.getParsedContent());
            }
        }
        
        // 2. RAG 检索法规（如果知识库已就绪）
        List<String> ragChunks = Collections.emptyList();
        if (ragEngine != null) {
            ragChunks = ragEngine.search(dto.getMessage(), 5);
        }
        String ragContext = CompliancePrompt.buildRagContext(ragChunks);
        
        // 3. 加载历史消息
        List<Message> messages = new ArrayList<>();
        messages.add(Message.builder()
            .role(Role.SYSTEM.getValue())
            .content(CompliancePrompt.SYSTEM_PROMPT)
            .build());
        
        // 加载历史对话
        List<ChatMessage> history = chatMessageService.getRecentMessages(
            dto.getSessionId(), MAX_HISTORY_ROUNDS * 2
        );
        for (ChatMessage h : history) {
            messages.add(Message.builder()
                .role(h.getRole())
                .content(h.getContent())
                .build());
        }
        
        // 4. 构建当前轮的用户消息
        StringBuilder currentMsg = new StringBuilder();
        if (StringUtils.hasText(fileContext)) {
            currentMsg.append(fileContext).append("\n\n");
        }
        if (StringUtils.hasText(ragContext)) {
            currentMsg.append(ragContext).append("\n\n");
        }
        currentMsg.append(dto.getMessage());
        
        messages.add(Message.builder()
            .role(Role.USER.getValue())
            .content(currentMsg.toString())
            .build());
        
        // 5. 调用千问
        String reply = qwenService.chatWithMessages(messages);
        
        // 6. 持久化消息
        chatMessageService.saveMessage(dto.getSessionId(), "user", dto.getMessage());
        chatMessageService.saveMessage(dto.getSessionId(), "assistant", reply);
        
        return reply;
    }
}
```

#### 4.3.4 接口设计

```
POST /api/compliance/chat
Content-Type: application/json

请求:
{
  "sessionId": 1,
  "fileId": 5,
  "message": "这份采购记录是否符合药品管理法？"
}

响应:
{
  "code": 200,
  "data": {
    "role": "assistant",
    "content": "## 合规判断：⚠️ 部分合规\n\n### 分析\n根据文件内容...\n\n### 法规依据\n《药品管理法》第五十三条...\n\n### 改进建议\n1. ...",
    "sessionId": 1,
    "messageId": 42
  }
}
```

---

### 4.4 对话历史（B4）

#### 4.4.1 接口设计

```
# 获取会话列表
GET /api/compliance/sessions?type=compliance
→ [{ id: 1, title: "采购记录审查", fileId: 5, updatedAt: "..." }, ...]

# 获取某会话的消息列表
GET /api/compliance/sessions/{sessionId}/messages
→ [{ id: 1, role: "user", content: "...", createdAt: "..." }, ...]

# 创建新会话
POST /api/compliance/sessions
{ "sessionType": "compliance", "fileId": 5 }

# 删除会话
DELETE /api/compliance/sessions/{sessionId}
```

---

### 4.5 文件列表管理（B5）

```
# 文件列表
GET /api/compliance/files
→ [{ id: 5, fileName: "采购记录.pdf", fileType: "pdf", fileSize: 1048576, 
      parseStatus: 1, createdAt: "..." }, ...]

# 删除文件（同步删除本地文件 + 数据库记录）
DELETE /api/compliance/files/{id}
```

---

## 五、需要新增的依赖

```xml
<!-- PDF 解析 -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.4</version>
</dependency>

<!-- Word/Excel 解析 -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.3.0</version>
</dependency>
```

---

## 六、QwenService 增强（支持多轮消息）

模块B 的合规对话需要传入多轮消息列表，现有 `QwenService` 只支持单轮。需要新增方法：

```java
// QwenService.java — 新增方法
/**
 * 多轮消息对话（合规对话及通用多轮场景使用）
 */
public String chatWithMessages(List<Message> messages) {
    Generation gen = new Generation();
    GenerationParam param = GenerationParam.builder()
            .apiKey(qwenConfig.getApiKey())
            .model("qwen-plus")           // 合规场景用更强模型
            .messages(messages)
            .resultFormat(GenerationParam.ResultFormat.MESSAGE)
            .topP(0.8)
            .temperature(0.7f)
            .maxTokens(4096)
            .build();
    try {
        GenerationResult result = gen.call(param);
        return result.getOutput().getChoices().get(0).getMessage().getContent();
    } catch (ApiException | NoApiKeyException | InputRequiredException e) {
        log.error("千问多轮对话调用异常", e);
        throw new BusinessException("AI服务暂时不可用，请稍后重试");
    }
}

/**
 * 带System Prompt的单轮对话（药品分析专用）
 */
public String chatWithSystem(String systemPrompt, String userPrompt) {
    List<Message> messages = List.of(
        Message.builder().role(Role.SYSTEM.getValue()).content(systemPrompt).build(),
        Message.builder().role(Role.USER.getValue()).content(userPrompt).build()
    );
    return chatWithMessages(messages);
}
```

---

## 七、异常场景处理

| 场景 | 处理方式 |
|------|---------|
| 文件格式不支持 | 上传时校验后缀名和MIME类型，返回 400 |
| PDF 加密/密码保护 | PDFBox 捕获异常，标记 parse_status=2 |
| Word 文件损坏 | POI 捕获异常，标记解析失败 |
| 解析内容为空 | 标记成功但提示用户文件无文本内容（可能为扫描件） |
| 文件已删除但对话未结束 | 对话时判断 file 是否存在，不存在则提示重新上传 |
| 历史消息过多超 Token | 只加载最近 10 轮对话，更早的截断 |
| 并发上传同名文件 | UUID 重命名，不会冲突 |
