# 05 API 与数据结构（场景一契约）

> 后端 `server.servlet.context-path` 当前为 `/api`，以下路径均基于该前缀。

## 1. 提交任务

### `POST /api/bid/compare`

`Content-Type: multipart/form-data`

表单字段建议：
- `fileA`: 待查标书文件
- `fileB`: 基准标书文件（MVP 推荐）
- `referenceBidId`: 可选，后续支持基准库模式

响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": "bid-20260312-001",
    "status": "PENDING"
  }
}
```

## 2. 查询进度

### `GET /api/bid/task/{taskId}/progress`

响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": "bid-20260312-001",
    "status": "RUNNING",
    "progress": 62,
    "statusText": "正在调用重排模型过滤候选片段"
  }
}
```

## 3. 获取报告

### `GET /api/bid/report/{taskId}`

响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": "bid-20260312-001",
    "overallRiskLevel": "HIGH",
    "overallSimilarity": 0.79,
    "summary": "发现多个关键技术参数段落高度雷同，疑似模板化复制。",
    "items": [
      {
        "chunkIdA": "A-12",
        "chunkIdB": "B-77",
        "score": 0.93,
        "riskLevel": "HIGH",
        "similarityType": "技术参数完全重合",
        "reason": "关键参数和限制条件几乎一致，仅替换供应商名称。",
        "textA": "...",
        "textB": "..."
      }
    ],
    "stats": {
      "highRiskCount": 3,
      "mediumRiskCount": 8,
      "lowRiskCount": 5
    }
  }
}
```

## 4. 错误响应建议

```json
{
  "code": 500,
  "message": "任务执行失败：重排模型调用超时",
  "data": null
}
```

## 5. 状态枚举

- `PENDING`：已创建任务，等待执行
- `RUNNING`：处理中
- `SUCCESS`：处理完成
- `FAILED`：处理失败

## 6. 报告字段最小集（前后端必须统一）

- `taskId`
- `overallRiskLevel`
- `overallSimilarity`
- `summary`
- `items[]`（至少包含 `score/riskLevel/reason/textA/textB`）
