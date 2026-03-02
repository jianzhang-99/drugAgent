# 模块D：通用能力 — 技术实现方案

> **版本**：v1.0 | **日期**：2026-03-02 | **技术栈**：Spring Boot 4.0 + DashScope SDK + SSE

---

## 一、模块概述

通用能力模块提供系统级的基础支撑能力，包括：千问大模型调用封装、多轮对话上下文管理、会话生命周期管理、SSE 流式输出等。这些能力被模块 A（药品分析）和模块 B（合规对话）共同依赖。

### 能力矩阵

| 能力 | 依赖方 | 优先级 |
|------|--------|:-----:|
| 千问 API 封装（单轮/多轮/流式） | A3, B3, C2 | P0 |
| 多轮对话上下文管理 | B3, B4 | P0 |
| 会话管理（CRUD） | B3, B4 | P1 |
| SSE 流式输出 | B3（增强体验） | P2 |
| 统一响应封装 `Result<T>` | 全局 | P0 |
| 全局异常处理 | 全局 | P0 |

---

## 二、包结构设计

```
com.liang.drugagent
├── service
│   └── QwenService.java                 // 千问调用服务（增强版）
├── session
│   └── SessionManager.java              // 会话上下文管理器
├── common
│   ├── Result.java                      // 统一响应封装
│   ├── BusinessException.java           // 业务异常
│   └── GlobalExceptionHandler.java      // 全局异常处理
└── config
    ├── QwenConfig.java                  // 千问配置（已有）
    ├── WebConfig.java                   // CORS跨域配置
    └── AsyncConfig.java                 // 异步执行配置
```

---

## 三、D1 — 千问 API 调用封装（增强版）

### 3.1 现状分析

现有 `QwenService` 仅支持单轮 prompt → response，需增强为：

| 能力 | 当前 | 增强后 |
|------|:---:|:-----:|
| 单轮对话 | ✅ | ✅ |
| 自定义 System Prompt | ❌ | ✅ |
| 多轮消息列表 | ❌ | ✅ |
| 流式输出 (SSE) | ❌ | ✅ |
| 超时/重试 | ❌ | ✅ |
| 模型选择 | 固定 qwen-turbo | 可配置 |

### 3.2 增强实现

```java
// QwenService.java — 增强版
@Slf4j
@Service
public class QwenService {

    private final QwenConfig qwenConfig;
    
    // ======================== 基础单轮对话 ========================
    
    /**
     * 简单单轮对话（保留向后兼容）
     */
    public String chat(String prompt) {
        return chatWithSystem("You are a helpful assistant.", prompt);
    }
    
    /**
     * 带 System Prompt 的单轮对话
     */
    public String chatWithSystem(String systemPrompt, String userPrompt) {
        List<Message> messages = List.of(
            Message.builder().role(Role.SYSTEM.getValue()).content(systemPrompt).build(),
            Message.builder().role(Role.USER.getValue()).content(userPrompt).build()
        );
        return chatWithMessages(messages, qwenConfig.getModel());
    }
    
    // ======================== 多轮对话 ========================
    
    /**
     * 多轮消息对话（核心方法）
     * @param messages 完整的消息列表（含system/user/assistant）
     * @param model    模型名称
     */
    public String chatWithMessages(List<Message> messages, String model) {
        Generation gen = new Generation();
        GenerationParam param = GenerationParam.builder()
                .apiKey(qwenConfig.getApiKey())
                .model(model != null ? model : qwenConfig.getModel())
                .messages(messages)
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .topP(0.8)
                .temperature(0.7f)
                .maxTokens(4096)
                .build();
        
        try {
            GenerationResult result = gen.call(param);
            String content = result.getOutput().getChoices()
                .get(0).getMessage().getContent();
            log.info("千问调用成功, model={}, messages数={}, 响应长度={}", 
                model, messages.size(), content.length());
            return content;
        } catch (ApiException | NoApiKeyException | InputRequiredException e) {
            log.error("千问调用异常, model={}", model, e);
            throw new BusinessException("AI 服务暂时不可用，请稍后重试");
        }
    }
    
    /** 多轮对话（使用默认模型） */
    public String chatWithMessages(List<Message> messages) {
        return chatWithMessages(messages, null);
    }
    
    // ======================== 流式对话 ========================
    
    /**
     * 流式对话（SSE），逐 token 返回
     * @param messages 消息列表
     * @param emitter  Spring SSE 发射器
     */
    public void streamChat(List<Message> messages, SseEmitter emitter) {
        Generation gen = new Generation();
        GenerationParam param = GenerationParam.builder()
                .apiKey(qwenConfig.getApiKey())
                .model(qwenConfig.getModel())
                .messages(messages)
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .topP(0.8)
                .temperature(0.7f)
                .maxTokens(4096)
                .incrementalOutput(true)    // 增量输出模式
                .build();
        
        try {
            Flowable<GenerationResult> stream = gen.streamCall(param);
            StringBuilder fullContent = new StringBuilder();
            
            stream.doOnNext(result -> {
                String chunk = result.getOutput().getChoices()
                    .get(0).getMessage().getContent();
                fullContent.append(chunk);
                
                // 发送SSE事件
                emitter.send(SseEmitter.event()
                    .data(chunk)
                    .name("message"));
                    
            }).doOnComplete(() -> {
                emitter.send(SseEmitter.event()
                    .data("[DONE]")
                    .name("done"));
                emitter.complete();
                log.info("流式对话完成, 总长度={}", fullContent.length());
                
            }).doOnError(error -> {
                log.error("流式对话异常", error);
                emitter.completeWithError(error);
                
            }).blockingSubscribe();   // 阻塞订阅
            
        } catch (Exception e) {
            log.error("流式调用初始化失败", e);
            emitter.completeWithError(e);
        }
    }
}
```

### 3.3 配置增强

```yaml
# application.yml 新增
aliyun:
  dashscope:
    api-key: sk-xxxxx
    model: qwen-plus              # 默认模型（可切换 qwen-turbo / qwen-max）
    embedding-model: text-embedding-v3
```

```java
// QwenConfig.java 增强
@Data
@Configuration
@ConfigurationProperties(prefix = "aliyun.dashscope")
public class QwenConfig {
    private String apiKey;
    private String model = "qwen-plus";
    private String embeddingModel = "text-embedding-v3";
}
```

---

## 四、D1 — 多轮对话上下文管理

### 4.1 上下文管理策略

```
问题：千问每次调用需要传入完整的消息历史，但消息过多会超出 Token 上限。

解法：滑动窗口 + 摘要策略
  ┌──────────────────────────────────────────────┐
  │ System Prompt (始终保留)                       │
  │ [摘要] 之前的对话主要讨论了...  (超过N轮时压缩)  │
  │ User: 第8轮问题                               │
  │ Assistant: 第8轮回答                           │
  │ User: 第9轮问题                               │
  │ Assistant: 第9轮回答                           │
  │ User: 第10轮问题（当前）                       │
  └──────────────────────────────────────────────┘
  保留最近 MAX_ROUNDS 轮，更早的压缩为摘要
```

### 4.2 实现代码

```java
// SessionManager.java
@Service
public class SessionManager {
    
    private final ChatMessageService chatMessageService;
    
    private static final int MAX_ROUNDS = 10;          // 最多保留10轮
    private static final int MAX_TOTAL_CHARS = 12000;   // 总字符上限约6000 Token
    
    /**
     * 构建完整的消息列表（含历史上下文）
     */
    public List<Message> buildMessages(Long sessionId, 
                                        String systemPrompt,
                                        String currentUserMessage) {
        List<Message> messages = new ArrayList<>();
        
        // 1. System Prompt
        messages.add(Message.builder()
            .role(Role.SYSTEM.getValue())
            .content(systemPrompt)
            .build());
        
        // 2. 历史消息（最近N轮）
        if (sessionId != null) {
            List<ChatMessage> history = chatMessageService
                .getRecentMessages(sessionId, MAX_ROUNDS * 2);
            
            // 计算总字符数，超限时截断早期消息
            int totalChars = systemPrompt.length() + currentUserMessage.length();
            List<ChatMessage> trimmed = new ArrayList<>();
            
            // 从最近的消息开始保留
            for (int i = history.size() - 1; i >= 0; i--) {
                int msgLen = history.get(i).getContent().length();
                if (totalChars + msgLen > MAX_TOTAL_CHARS) break;
                totalChars += msgLen;
                trimmed.add(0, history.get(i));
            }
            
            for (ChatMessage h : trimmed) {
                messages.add(Message.builder()
                    .role(h.getRole())
                    .content(h.getContent())
                    .build());
            }
        }
        
        // 3. 当前用户消息
        messages.add(Message.builder()
            .role(Role.USER.getValue())
            .content(currentUserMessage)
            .build());
        
        return messages;
    }
}
```

---

## 五、D2 — 会话管理

### 5.1 接口设计

```
# 创建新会话
POST /api/sessions
{ "sessionType": "compliance", "fileId": 5, "title": "采购记录审查" }
→ { "id": 1, "sessionType": "compliance", "title": "采购记录审查", "createdAt": "..." }

# 获取会话列表
GET /api/sessions?type=compliance
→ [{ "id": 1, "title": "采购记录审查", "fileId": 5, "updatedAt": "..." }, ...]

# 获取会话详情（含最近消息）
GET /api/sessions/{id}
→ { "id": 1, "title": "...", "messages": [...] }

# 删除会话（级联删除消息）
DELETE /api/sessions/{id}
→ { "message": "删除成功" }
```

### 5.2 自动标题生成

```java
// 首次对话后自动生成会话标题
public void autoGenerateTitle(Long sessionId, String firstMessage) {
    // 截取前50字作为标题
    String title = firstMessage.length() > 50 
        ? firstMessage.substring(0, 50) + "..." 
        : firstMessage;
    chatSessionMapper.updateTitle(sessionId, title);
}
```

---

## 六、D3 — 流式输出（SSE）

### 6.1 后端 SSE 接口

```java
// ComplianceController.java
@GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamChat(@RequestParam Long sessionId,
                              @RequestParam(required = false) Long fileId,
                              @RequestParam String message) {
    SseEmitter emitter = new SseEmitter(60_000L);   // 60秒超时
    
    // 异步执行
    CompletableFuture.runAsync(() -> {
        try {
            // 1. 构建消息（同 chat 方法逻辑）
            List<Message> messages = buildChatMessages(sessionId, fileId, message);
            
            // 2. 流式调用
            qwenService.streamChat(messages, emitter);
            
            // 3. 完成后保存消息到数据库
            // (fullContent 在 streamChat 中已收集)
            
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    });
    
    return emitter;
}
```

### 6.2 前端 SSE 接收

```javascript
// 前端 EventSource 接收
function startStreamChat(sessionId, message) {
    const url = `/api/compliance/chat/stream?sessionId=${sessionId}&message=${encodeURIComponent(message)}`;
    const eventSource = new EventSource(url);
    
    let fullContent = '';
    
    eventSource.addEventListener('message', (event) => {
        const chunk = event.data;
        fullContent += chunk;
        // 逐字追加渲染到对话气泡
        updateMessageBubble(fullContent);
    });
    
    eventSource.addEventListener('done', () => {
        eventSource.close();
        // 标记消息完成，启用 Markdown 渲染
        finalizeMessage(fullContent);
    });
    
    eventSource.onerror = () => {
        eventSource.close();
        showError('连接中断');
    };
}
```

---

## 七、统一响应封装 & 全局异常

### 7.1 Result<T>

```java
// Result.java
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result<T> {
    private int code;
    private String message;
    private T data;
    
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }
    
    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }
    
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }
    
    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }
}
```

### 7.2 全局异常处理

```java
// GlobalExceptionHandler.java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusiness(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }
    
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<?> handleFileSize(MaxUploadSizeExceededException e) {
        return Result.error(413, "文件大小超过限制");
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return Result.error(400, msg);
    }
    
    @ExceptionHandler(Exception.class)
    public Result<?> handleGeneral(Exception e) {
        log.error("系统异常", e);
        return Result.error("系统内部错误，请联系管理员");
    }
}
```

### 7.3 CORS 跨域配置

```java
// WebConfig.java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOriginPatterns("*")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
```

### 7.4 异步配置

```java
// AsyncConfig.java
@Configuration
@EnableAsync
public class AsyncConfig {
    
    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("drug-agent-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```
