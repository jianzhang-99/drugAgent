import request from './request'

// 上传文件
export const uploadFile = (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/compliance/file/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
    })
}

// 发送对话
export const sendChat = async (data) => {
    // 临时绕过后端，直接调用通义千问API
    const apiKey = 'sk-a433391322b54c3db665851b085ba4d4'
    const payload = {
        model: 'qwen-plus',
        input: {
            messages: [
                {
                    role: 'system',
                    content: '你是专业的医药监管AI助手。'
                },
                {
                    role: 'user',
                    content: data.message
                }
            ]
        },
        parameters: {}
    }

    try {
        const response = await fetch('/dashscope/api/v1/services/aigc/text-generation/generation', {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${apiKey}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(payload)
        })

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`)
        }

        const json = await response.json()
        return {
            content: json.output.text
        }
    } catch (error) {
        console.error('DashScope API Error:', error)
        throw error
    }
}

// 获取会话列表
export const getSessions = (type = 'compliance') => {
    return request.get('/compliance/sessions', { params: { type } })
}

// 获取会话消息
export const getMessages = (sessionId) => {
    return request.get(`/compliance/sessions/${sessionId}/messages`)
}

// 创建新会话
export const createSession = (data) => {
    return request.post('/compliance/sessions', data)
}

// 删除会话
export const deleteSession = (id) => {
    return request.delete(`/compliance/sessions/${id}`)
}

// 获取文件列表
export const getFiles = () => request.get('/compliance/files')

// 删除文件
export const deleteFile = (id) => request.delete(`/compliance/files/${id}`)
