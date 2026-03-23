# 多模型支持与 MiniMax 接入改造计划

## 1. 文档目的

本文档用于指导 `drug-agent` 从“默认只依赖当前百炼/千问接入”升级为“支持多模型提供商切换”的架构，并在此基础上扩展 MiniMax 作为新的大模型提供方。

本次改造的目标不是简单把一个 MiniMax 调用塞进现有代码，而是：

- 让项目支持选择不同大模型
- 支持后续继续扩展更多 provider
- 避免模型调用代码散落在 workflow 和 service 中
- 保留现有百炼能力，同时平滑加入 MiniMax

## 2. 当前现状

从当前代码结构看，模型调用主要存在以下特点：

- `AgentChatService` 直接依赖 `ChatClient.Builder`
- `QwenService` 也是直接基于 `ChatClient` 调用
- `application.yml` 当前只配置了 DashScope
- 项目还没有“模型提供商抽象层”

这意味着当前架构的问题是：

- provider 被写死在底层配置里
- 上层业务无法明确控制“这次请求用哪个模型”
- 想加 MiniMax 时容易出现复制一份 service 的情况
- 后续如果再扩展 OpenAI、DeepSeek、智谱等，会越来越乱

## 3. 目标定义

本次改造完成后，应达到以下状态：

- 项目支持至少两种 provider：
  - `dashscope`
  - `minimax`
- 支持全局默认 provider 配置
- 支持按场景或按用途选择 provider
- 模型调用入口统一，不再在业务层直接依赖某个厂商 SDK 或配置
- API Key 不进入代码仓库，只通过环境变量或本地私密配置注入

## 4. 核心设计原则

### 4.1 先抽象，再接厂商

不要直接在现有 `AgentChatService` 里堆 `if provider == minimax` 的逻辑。  
应该先抽一个统一的 LLM 调用层。

### 4.2 业务只关心“能力”，不关心“厂商”

例如：

- workflow 路由判别
- 通用问答
- 报告润色
- 解释说明

这些都属于能力层。业务层应只说“我要一个文本生成能力”，而不应直接关心它来自 DashScope 还是 MiniMax。

### 4.3 默认 provider 和指定 provider 并存

建议既支持：

- 全局默认 provider
- 按请求指定 provider
- 按能力类型选择 provider

这样后续实验和切换成本最低。

### 4.4 所有密钥必须走环境变量

MiniMax API Key 和 DashScope API Key 都不要写死在仓库中。

建议统一使用：

- `DASHSCOPE_API_KEY`
- `MINIMAX_API_KEY`

如果本地开发需要，也只允许写在本地不提交的配置文件中。

## 5. 总体改造方案

建议将项目中的模型能力拆成以下几层：

```text
业务层 (workflow / router / report)
-> 统一 LLM Facade
-> Provider Router
-> DashScope Provider / MiniMax Provider
-> 厂商 API
```

### 5.1 新增统一抽象

建议新增以下结构：

| 组件 | 作用 |
|---|---|
| `LlmProviderType` | 枚举当前支持的 provider |
| `LlmRequest` | 统一请求对象 |
| `LlmResponse` | 统一响应对象 |
| `LlmClient` | 统一调用接口 |
| `DashScopeLlmClient` | 百炼实现 |
| `MiniMaxLlmClient` | MiniMax 实现 |
| `LlmClientFactory` | 根据 provider 选择实现 |
| `LlmFacadeService` | 给业务层使用的统一门面 |

## 6. 推荐类设计

### 6.1 provider 枚举

```java
public enum LlmProviderType {
    DASHSCOPE,
    MINIMAX
}
```

### 6.2 统一请求对象

```java
public class LlmRequest {
    private String systemPrompt;
    private String userPrompt;
    private String sessionId;
    private String model;
    private Double temperature;
    private LlmProviderType provider;
    private Map<String, Object> metadata;
}
```

### 6.3 统一响应对象

```java
public class LlmResponse {
    private String content;
    private String model;
    private String provider;
    private String requestId;
    private Map<String, Object> raw;
}
```

### 6.4 统一接口

```java
public interface LlmClient {
    LlmProviderType support();
    String chat(LlmRequest request);
    Flux<String> streamChat(LlmRequest request);
}
```

## 7. 你的项目里应该怎么落

## Phase 1：先抽统一 LLM 调用层

### 目标

先把当前“写死在 `AgentChatService` 里的百炼调用”抽出来，避免 MiniMax 一接入就造成结构继续恶化。

### 任务清单

| 任务 | 预估 | Done Criteria |
|---|---:|---|
| 新增 `LlmProviderType` | 1h | 枚举定义完成 |
| 新增 `LlmRequest / LlmResponse` | 2h | 统一输入输出完成 |
| 新增 `LlmClient` 接口 | 1h | 统一调用协议完成 |
| 新增 `LlmClientFactory` | 2h | 能根据 provider 返回对应实现 |
| 新增 `LlmFacadeService` | 3h | 业务层只依赖 facade |

### 怎么实现

建议新增目录：

```text
src/main/java/com/liang/drugagent/llm/
src/main/java/com/liang/drugagent/llm/model/
src/main/java/com/liang/drugagent/llm/provider/
```

建议类文件：

- `llm/LlmClient.java`
- `llm/LlmFacadeService.java`
- `llm/LlmClientFactory.java`
- `llm/model/LlmProviderType.java`
- `llm/model/LlmRequest.java`
- `llm/model/LlmResponse.java`

## Phase 2：把当前 DashScope 调用迁移进去

### 目标

先把你当前已经在用的 DashScope 包成第一个标准 provider，实现“旧能力迁移而不回归”。

### 任务清单

| 任务 | 预估 | Done Criteria |
|---|---:|---|
| 实现 `DashScopeLlmClient` | 4h | 可替代当前基础调用 |
| `AgentChatService` 改为依赖 `LlmFacadeService` | 4h | 不再直接依赖 `ChatClient.Builder` |
| `QwenService` 改为复用统一 facade | 2h | 不再重复造轮子 |
| 验证通用问答和流式输出不回归 | 4h | 现有接口功能正常 |

### 怎么实现

当前你项目里的 `AgentChatService` 可以保留，但职责要调整为：

- 负责 system prompt、chat memory、agentType 映射
- 不再负责直接挑底层厂商

也就是说：

- `AgentChatService` 继续保留业务语义
- `LlmFacadeService` 接管 provider 选择和底层发送

## Phase 3：新增 MiniMax Provider

### 目标

在统一抽象层下接入 MiniMax，不破坏现有 DashScope 调用。

### 任务清单

| 任务 | 预估 | Done Criteria |
|---|---:|---|
| 新增 `MiniMaxProperties` | 2h | 支持从配置读取 baseUrl / model / apiKey |
| 实现 `MiniMaxLlmClient` | 6h | 支持文本 chat |
| 实现 MiniMax 流式输出 | 6h | 支持 SSE/stream |
| 增加请求异常处理 | 3h | 超时、401、429、5xx 有统一包装 |
| 增加 provider 选择测试 | 3h | 能正常切到 MiniMax |

### 怎么实现

建议 MiniMax 配置项设计为：

```yaml
llm:
  provider: dashscope
  default-model: qwen-plus

minimax:
  enabled: true
  api-key: ${MINIMAX_API_KEY:}
  base-url: https://api.minimax.io
  model: MiniMax-M1
  timeout-ms: 30000
```

注意：

- 不要把 MiniMax key 写进 `application.yml`
- 不要把你现在手里的正式 key 写进仓库
- 只通过环境变量 `MINIMAX_API_KEY` 注入

### MiniMax 请求层建议

建议 `MiniMaxLlmClient` 不要直接散着写 HTTP 调用，而是统一封装：

- 请求头生成
- 模型名映射
- 超时和错误处理
- 响应解析

如果后续你发现 Spring AI 对 MiniMax 有成熟适配，也可以再切过去；但第一版建议自己封装一个轻量 client，会更可控。

## Phase 4：支持按 provider 选择模型

### 目标

让你的系统不仅“接了 MiniMax”，还真正支持“选不同模型”。

### 任务清单

| 任务 | 预估 | Done Criteria |
|---|---:|---|
| 增加全局默认 provider 配置 | 1h | 不传时可用默认值 |
| 支持按能力类型指定 provider | 3h | 路由、问答、解释可分别配置 |
| 支持按请求 metadata 指定 provider | 3h | 特定实验可临时切换 |
| 增加 provider 选择日志 | 2h | 每次调用可追踪 |

### 怎么实现

建议优先支持三种选择模式：

#### 模式一：全局默认

```yaml
llm:
  default-provider: dashscope
```

#### 模式二：按用途配置

```yaml
llm:
  routing-provider: minimax
  chat-provider: dashscope
  report-provider: minimax
```

#### 模式三：按请求指定

例如在 `metadata` 中临时传：

```json
{
  "provider": "MINIMAX"
}
```

推荐优先级：

1. 请求显式指定
2. 能力类型配置
3. 全局默认配置

## Phase 5：让大模型进入正确业务位置

### 目标

不是“接上 MiniMax 就完事”，而是让它在你系统里发挥真正价值。

### 建议优先使用 MiniMax 的场景

#### 5.1 workflow 路由判别

最适合先接 MiniMax 的位置之一。

原因：

- 输入短
- 成本可控
- 结果可结构化
- 出错可回退到规则

#### 5.2 审查报告解释和润色

第二适合的位置。

原因：

- 不参与最终规则裁决
- 能明显提升产品体验
- 即使偶发不稳定，也不会伤害底层可信度

#### 5.3 通用问答 fallback

第三适合的位置。

原因：

- 当前你已经有问答骨架
- 替换 provider 比较轻

### 暂时不建议先用 MiniMax 的位置

- 最终风险判定
- 规则引擎替代
- 核心分数计算

这些仍应继续由结构化规则和风险融合逻辑负责。

## 8. 推荐配置设计

建议在 `application.yml` 中新增：

```yaml
llm:
  default-provider: dashscope
  routing-provider: minimax
  chat-provider: dashscope
  report-provider: minimax
  stream-provider: dashscope

minimax:
  enabled: true
  api-key: ${MINIMAX_API_KEY:}
  base-url: ${MINIMAX_BASE_URL:https://api.minimax.io}
  model: ${MINIMAX_MODEL:MiniMax-M1}
  timeout-ms: 30000

dashscope:
  enabled: true
  model: qwen-plus
```

说明：

- 这里的 `api-key` 只从环境变量读取
- 默认 provider 先保留 DashScope，避免大改时全部依赖新 provider
- 路由和报告可以先切给 MiniMax 做实验

## 9. 具体改造步骤建议

建议严格按下面顺序做。

### Step 1

先抽统一 `LlmClient` 层，不接 MiniMax。

### Step 2

把当前 DashScope 迁进去，确保旧能力不回归。

### Step 3

再实现 `MiniMaxLlmClient`。

### Step 4

加 provider 配置开关和日志。

### Step 5

先把 MiniMax 接到：

- workflow 路由判别
- 报告解释

### Step 6

验证效果后，再考虑让通用 chat 或流式输出切过去。

## 10. 测试建议

### 单元测试

- `LlmClientFactory` 能否正确返回 provider
- MiniMax 配置为空时是否正确降级
- provider 枚举解析是否稳定

### 集成测试

- DashScope 正常调用不回归
- MiniMax 正常返回文本
- MiniMax 超时、401、429、5xx 是否有统一错误
- workflow 路由切 MiniMax 后是否能回退

### 手工验证清单

- 默认 provider 为 DashScope 时，系统正常工作
- 切成 MiniMax 后，普通 chat 正常返回
- 路由 provider 单独配置为 MiniMax 时，场景判别正常
- 报告 provider 单独配置为 MiniMax 时，解释文案正常

## 11. 风险与规避

| 风险 | 影响 | 概率 | 规避方式 |
|---|---|---|---|
| 直接把 MiniMax 硬塞进现有 service | 高 | 高 | 先抽统一 provider 层 |
| API Key 泄露 | 高 | 高 | 只走环境变量，禁止写入仓库 |
| 全量切换导致现有链路回归 | 高 | 中 | 保留 DashScope 作为默认 provider |
| MiniMax 流式接口适配复杂 | 中 | 中 | 先接非流式，后接流式 |
| 多 provider 日志不可观察 | 中 | 高 | 记录 provider、model、耗时、错误码 |

## 12. 你的项目里最建议优先改的文件

- `src/main/java/com/liang/drugagent/service/AgentChatService.java`
- `src/main/java/com/liang/drugagent/service/QwenService.java`
- `src/main/java/com/liang/drugagent/config/AiConfiguration.java`
- `src/main/resources/application.yml`
- `src/main/resources/application-local.yml`

建议新增目录：

- `src/main/java/com/liang/drugagent/llm/`
- `src/main/java/com/liang/drugagent/llm/model/`
- `src/main/java/com/liang/drugagent/llm/provider/`
- `src/main/java/com/liang/drugagent/config/properties/`

## 13. 安全建议

你刚才已经提供了一个正式 MiniMax API Key。  
这类 key 不应：

- 写入代码仓库
- 写入公开文档
- 直接提交到 `application-local.yml`

建议你现在就做两件事：

1. 将当前 key 改为环境变量 `MINIMAX_API_KEY`
2. 如果该 key 已在不安全位置暴露，建议尽快在 MiniMax 控制台轮换

## 14. 结论

接入 MiniMax 完全可行，而且非常值得做。  
但正确做法不是“再加一个模型 service”，而是：

- 先抽统一多模型架构
- 再把 DashScope 迁进去
- 再平滑接入 MiniMax
- 最后按用途切不同 provider

这样你得到的不是“一次接入 MiniMax”，而是“项目以后可以长期扩展不同大模型”的基础能力。
