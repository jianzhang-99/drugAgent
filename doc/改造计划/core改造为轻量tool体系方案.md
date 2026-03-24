# core 改造为轻量 tool 体系方案

## 1. 改造目标

这次改造的目标不是搭一个很重的工具平台，而是做一版：

1. 简单
2. 好懂
3. 够用
4. 能支撑后续继续加 tool

核心思路只有一句话：

**把当前 `core` 里“适合被复用和被调用”的能力收敛到 `tool` 目录，但不做过度抽象。**

也就是说，这次不是做一整套复杂框架，而是先完成：

1. 目录语义改正确
2. 文档解析能力工具化
3. 增加一个很薄的 `ToolExecutor`
4. 让后续 tool 有统一入口

---

## 2. 当前判断

当前已经有：

- `src/main/java/com/liang/drugagent/core/document`

里面这套代码其实已经很接近一个工具了，只是名字还是 `core`，调用方式还是 `service` 风格。

当前最值得收敛成 tool 的能力是：

1. 文档解析
2. 文件类型路由
3. 通用文档结构输出

当前不值得过早抽象的东西是：

1. `ToolRegistry`
2. `ToolRequest`
3. `ToolContext`
4. `ToolDefinition`
5. `common` 常量层
6. 一堆空壳 model

这些都属于“以后可能需要”，但不是“现在必须要有”。

---

## 3. 改造原则

### 3.1 能放一起就放一起

如果文件数量不多，就不要为了“层次清晰”再拆出很多子目录。

### 3.2 能直接调用就不要包协议

如果当前 tool 只有本地 Java 调用，就优先保留明确的方法签名，不要急着统一成大而全的请求对象。

### 3.3 能少一个类就少一个类

如果某个类只是包一层转发，没有新增语义，就优先删除。

### 3.4 先解决当前问题，不预付未来复杂度

当前真正的问题是：

1. `core` 语义不准
2. 文档解析重复分发
3. 后续 tool 没有统一入口

所以方案只围绕这三个问题展开。

---

## 4. 推荐目录结构

建议把当前 `core/document` 收口到：

`src/main/java/com/liang/drugagent/tool`

目录尽量保持轻量：

```text
src/main/java/com/liang/drugagent/tool
├── ToolExecutor.java
├── ToolResult.java
└── document
    ├── DocumentParseTool.java
    ├── DocumentParser.java
    ├── DocDocumentParser.java
    ├── DocxDocumentParser.java
    ├── MarkdownDocumentParser.java
    ├── DocumentBlock.java
    ├── DocumentParseResult.java
    ├── DocumentSectionNode.java
    ├── DocumentTextNormalizer.java
    └── DocumentSectionRecognizer.java
```

这版结构故意不再拆：

1. `executor`
2. `registry`
3. `model`
4. `support`
5. `common`

原因很简单：

**当前文件数量还不多，平铺目录更适合演示版和快速迭代。**

---

## 5. 最小化设计

### 5.1 `ToolExecutor`

只保留一个统一执行入口。

职责：

1. 管理当前可用 tool
2. 按名称找到 tool
3. 执行 tool

不要承担太多职责：

1. 不做注册中心
2. 不做复杂协议转换
3. 不做权限系统
4. 不做异步调度

它本质上只是一个“统一入口”。

### 5.2 `ToolResult`

建议保留一个很薄的结果包装，用于统一成功/失败返回。

例如只保留：

1. `success`
2. `data`
3. `message`

不需要先设计：

1. `errorCode`
2. `metadata`
3. `options`
4. `trace payload`

### 5.3 `DocumentParseTool`

文档解析先作为第一批、也是当前唯一明确的通用 tool。

职责：

1. 统一接收文档解析请求
2. 内部根据文件类型选择 parser
3. 返回 `DocumentParseResult`

当前它完全可以自己管理 `doc/docx/md` 三个 parser，不必再套更多层。

---

## 6. 不建议现在引入的设计

下面这些设计不是永远不需要，而是：

**现在先不要上。**

### 6.1 `ToolRegistry`

当前 tool 数量很少，直接让 Spring 注入 `List<...>` 或者在 `ToolExecutor` 里手工组装都够了。

### 6.2 `ToolRequest`

如果每个 tool 的入参差异很大，硬包成一个统一请求对象，反而会让代码更绕。

### 6.3 `ToolContext`

如果当前没有明确的统一上下文透传需求，这个类就很容易变成空壳。

### 6.4 `ToolDefinition`

如果当前没有做动态 tool 展示、动态 schema、agent 自动发现，这个类也没有必要。

### 6.5 过细子目录

`document/model`、`document/support`、`document/parser` 这些目录不是不能拆，而是现在没必要。

---

## 7. 文档解析 tool 的落地方式

当前 `core/document/DocumentParseService` 最适合直接改造成：

- `tool/document/DocumentParseTool`

建议职责不变：

1. 输入：`docId`、`filename`、`inputStream`
2. 内部判断扩展名
3. 路由到 `DocDocumentParser`、`DocxDocumentParser`、`MarkdownDocumentParser`
4. 输出统一 `DocumentParseResult`

也就是说，当前真正需要的 tool 化，不是额外再套层，而是：

**把原来的通用 service，正式收口成一个通用 tool。**

---

## 8. ToolExecutor 建议形态

### 8.1 最简单版本

建议先做成一个非常朴素的执行器。

例如：

```java
public interface ToolExecutor {
    <T> ToolResult<T> execute(String toolName, Object input);
}
```

或者如果怕 `Object input` 太泛，可以更进一步简化：

```java
public interface ToolExecutor {
    DocumentParseResult parseDocument(String docId, String filename, InputStream inputStream) throws IOException;
}
```

如果当前只有一个 `document.parse`，第二种甚至更适合项目现阶段。

### 8.2 推荐现实做法

当前阶段更建议：

1. `ToolExecutor` 先只支持少量明确方法
2. 不强追求所有 tool 都走完全一致的泛型签名
3. 等 tool 变多之后，再统一抽象

也就是说：

**先保留“可读性”，再追求“抽象一致性”。**

---

## 9. 场景层如何接入

改造之后，场景层不应该再直接依赖：

1. `DocxDocumentParser`
2. `DocDocumentParser`
3. `MarkdownDocumentParser`

也不应该再保留重复分发逻辑。

更合理的方式是：

1. 删除或瘦身 `TenderDocumentParseService`
2. 场景层统一通过 `DocumentParseTool` 或 `ToolExecutor` 调用解析能力

如果觉得场景层直接调 `ToolExecutor` 不够直观，可以保留一个很薄的门面：

- `TenderDocumentParseService`

但这个类只做一件事：

**转调通用 tool，不再自己做文件类型判断。**

---

## 10. 推荐迁移步骤

### 10.1 第一步：先改目录

动作：

1. 把 `core/document` 迁到 `tool/document`
2. 相关类名只做必要调整
3. 保持现有逻辑不变

目标：

先把位置和语义改正确。

### 10.2 第二步：把 `DocumentParseService` 改成 `DocumentParseTool`

动作：

1. 改类名
2. 保留原有解析路由逻辑
3. 继续使用现有 parser

目标：

先把第一批 tool 落下来。

### 10.3 第三步：新增极简 `ToolExecutor`

动作：

1. 新增一个很薄的 `ToolExecutor`
2. 先只接入 `DocumentParseTool`
3. 保证场景层有统一入口

目标：

先有统一入口，再考虑后续扩展。

### 10.4 第四步：清理场景重复逻辑

动作：

1. 清理 `TenderDocumentParseService` 里的扩展名判断
2. 改成调用 `DocumentParseTool` 或 `ToolExecutor`

目标：

去掉重复分发，完成第一轮工具收口。

---

## 11. 最终建议

这次方案建议用“轻量工具化”来理解，而不是“平台化工具框架”。

更具体地说：

1. `core` 改成 `tool`
2. 只保留当前明确需要的类
3. 只先落 `document.parse` 这一个样板 tool
4. 只引入一个很薄的 `ToolExecutor`
5. 不提前设计一堆还用不上的抽象层

当前最适合项目状态的落点不是：

**“先搭完一整套工具框架。”**

而是：

**“先把文档解析工具化，并给未来工具留一个简单统一入口。”**

这样做的好处是：

1. 改动小
2. 理解成本低
3. 能马上落地
4. 后续真的有第二个、第三个 tool 再继续抽象也不迟
