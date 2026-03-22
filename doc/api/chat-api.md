# 聊天会话 API 文档

## 基础信息

- 基础路径: `http://localhost:8080/api`
- 认证: 无（Demo阶段）
- 字符编码: UTF-8

## 会话管理

### 1. 获取会话列表

**GET** `/sessions`

**响应示例:**
```json
{
  "data": [
    {
      "id": "uuid-xxx",
      "title": "标书审查会话",
      "scene": "tender",
      "createdAt": "2026-03-22T10:00:00",
      "updatedAt": "2026-03-22T10:30:00"
    }
  ]
}
```

### 2. 获取会话详情

**GET** `/sessions/{id}`

**响应示例:**
```json
{
  "id": "uuid-xxx",
  "title": "标书审查会话",
  "scene": "tender",
  "messages": [
    {
      "id": "msg-uuid",
      "role": "user",
      "content": "帮我审查这份标书",
      "createdAt": "2026-03-22T10:00:00"
    },
    {
      "id": "msg-uuid-2",
      "role": "assistant",
      "content": "好的，请上传标书文件",
      "metadata": "{\"scene\":\"tender\",\"riskLevel\":\"LOW\"}",
      "createdAt": "2026-03-22T10:00:05"
    }
  ]
}
```

### 3. 创建会话

**POST** `/sessions`

**请求体:**
```json
{
  "title": "新会话",
  "scene": "general"
}
```

### 4. 更新会话标题

**PUT** `/sessions/{id}/title`

**请求体:**
```json
{
  "title": "新的标题"
}
```

### 5. 删除会话

**DELETE** `/sessions/{id}`

> 软删除，会话数据保留但不会在列表中显示

### 6. 搜索会话

**GET** `/sessions/search?q=关键词`

## 消息管理

### 7. 获取消息列表

**GET** `/sessions/{sessionId}/messages`

### 8. 发送消息

**POST** `/sessions/{sessionId}/messages`

**请求体:**
```json
{
  "role": "user",
  "content": "帮我审查这份标书",
  "metadata": null
}
```

**响应示例:**
```json
{
  "id": "msg-new-uuid",
  "sessionId": "session-uuid",
  "role": "user",
  "content": "帮我审查这份标书",
  "createdAt": "2026-03-22T10:00:00"
}
```
