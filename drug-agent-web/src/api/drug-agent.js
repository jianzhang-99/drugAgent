import request from './request'

// Drug Agent 对话入口
export const sendDrugAgentChat = (data) => {
  return request.post('/agent/drug/chat', data)
}

// Drug Agent SSE 流式对话
export const streamDrugAgentChat = async (data, handlers = {}) => {
  const response = await fetch('/api/agent/drug/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream'
    },
    body: JSON.stringify(data)
  })

  if (!response.ok || !response.body) {
    throw new Error(`SSE 请求失败: ${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  const emit = (eventName, payload) => {
    if (eventName === 'meta' && handlers.onMeta) handlers.onMeta(payload)
    if (eventName === 'delta' && handlers.onDelta) handlers.onDelta(payload)
    if (eventName === 'done' && handlers.onDone) handlers.onDone(payload)
    if (eventName === 'error' && handlers.onError) handlers.onError(payload)
  }

  const processEventBlock = (block) => {
    const lines = block.split('\n')
    let eventName = 'message'
    const dataLines = []

    lines.forEach((line) => {
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trim())
      }
    })

    if (!dataLines.length) return
    const rawData = dataLines.join('\n')
    let payload = rawData
    try {
      payload = JSON.parse(rawData)
    } catch (error) {
      // Plain text chunks are expected for delta events.
    }
    emit(eventName, payload)
  }

  while (true) {
    const { value, done } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })
    const blocks = buffer.split('\n\n')
    buffer = blocks.pop() || ''
    blocks.forEach(processEventBlock)
  }

  if (buffer.trim()) {
    processEventBlock(buffer)
  }
}
